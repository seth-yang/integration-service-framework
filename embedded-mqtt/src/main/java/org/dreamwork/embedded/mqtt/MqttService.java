package org.dreamwork.embedded.mqtt;

import org.dreamwork.embedded.mqtt.data.IncomingMessage;
import org.dreamwork.embedded.mqtt.data.MqttConfig;
import org.dreamwork.embedded.mqtt.data.OutgoingMessage;
import org.dreamwork.concurrent.TaskGroup;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.integration.api.services.IMqttListener;
import org.dreamwork.integration.api.services.IMqttService;
import org.dreamwork.util.IDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MqttService implements IMqttService, IDisposable {
    private static final Pattern PATTERN        = Pattern.compile ("^mqtt\\.(.*?)\\.url$");
    private static final String URL             = "url";
    private static final String USER            = "user";
    private static final String PASSWORD        = "password";
    private static final String CLIENT_ID       = "clientId";
    private static final String RECONNECT       = "auto-reconnect";
    private static final String KEY_CAPACITY    = "mqtt.message.queue.capacity";
    private static final String KEY_WORKERS     = "mqtt.message.queue.workers";
    private static final int DEFAULT_CAPACITY   = 64;
    private static final int DEFAULT_WORKERS    = 4;

    private static final Lock LOCKER = new ReentrantLock ();

    /**
     * mqttManager 已连接的映射
     */
    private static final Map<String, MqttManager> managers = new ConcurrentHashMap<> ();

    /**
     * mqttConfig 所有配置映射
     */
    private static final Map<String, MqttConfig> configs = new ConcurrentHashMap<> ();

    private final TaskGroup<IncomingMessage> incomingTasks;
    private final TaskGroup<OutgoingMessage> outgoingTasks;

    private final Logger logger = LoggerFactory.getLogger (MqttService.class);

    public MqttService (IConfiguration conf) {
        // 加载所有的配置
        loadConfigs (conf);

        // 创建并启动任务组
        int capacity  = conf.getInt (KEY_CAPACITY, DEFAULT_CAPACITY);
        int workers   = conf.getInt (KEY_WORKERS, DEFAULT_WORKERS);
        incomingTasks = new TaskGroup<> ("mqtt.incoming", capacity, workers);
        outgoingTasks = new TaskGroup<> ("mqtt.outgoing", capacity, workers);
        incomingTasks.start (im -> {
            if (im.listener != null) {
                try {
                    im.listener.onMessage (im.topic, im.message.getPayload ());
                } catch (Throwable ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        });
        outgoingTasks.start (om -> {
            if (om.manager != null) {
                try {
                    om.manager.send (om.topic, om.content);
                } catch (Throwable ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        });
    }

    /**
     * 向 mqtt 服务器发布一条消息
     *
     * @param configName mqtt配置名称
     * @param topic      主题
     * @param content    内容
     */
    @Override
    public void publish (String configName, String topic, byte[] content) {
        MqttManager manager = getMqttManager (configName);
        if (manager != null) {
            if (manager.isDisconnected ()) {
                manager.connect ();
            }
            if (!outgoingTasks.offer (new OutgoingMessage (topic, content, manager))) {
                logger.warn ("cannot publish message of topic: " + topic);
            }
        }
    }

    /**
     * 从 mqtt 服务器订阅一条消息
     * @param configName mqtt配置名称
     * @param topic      主题
     * @param listener   监听器
     */
    @Override
    public void subscribe (String configName, String topic, IMqttListener... listener) {
        MqttManager manager = getMqttManager (configName);
        if (manager == null) {
            throw new NullPointerException ("mqtt named: " + configName + " not exists");
        }
        if (manager.isDisconnected ()) {
            manager.connect ();
        }
        manager.subscribe (topic, listener);
    }

    @Override
    public void unsubscribe (String configName, String... topics) {
        MqttManager manager = managers.get (configName);
        if (manager != null && manager.isConnected ()) {
            manager.unsubscribe (topics);
        } else {
            logger.warn ("no mqtt manager named {} or the manager is not connected.", configName);
        }
    }

    @Override
    public Iterable<String> getAllNames () {
        return configs.keySet ();
    }

    /**
     * 向 mqtt 服务提供程序注册一个 mqtt 连接配置.
     * <p>其中有效的配置项名称为：<ul>
     *     <li>{@code url}: mqtt 服务器的url</li>
     *     <li>{@code user}: 登录 mqtt 服务器的用户名</li>
     *     <li>{@code password}: 登录 mqtt 服务器的密码</li>
     *     <li>{@code auto-reconnect}: true 代表自动重连，否则为 false </li>
     * </ul>
     *
     * @param name  配置名称
     * @param props 配置项
     */
    @Override
    public void register (String name, Properties props) {
        boolean reconnect = true;
        MqttConfig config = new MqttConfig ();
        config.name       = name;
        config.url        = props.getProperty (URL);
        config.username   = props.getProperty (USER);
        config.password   = props.getProperty (PASSWORD);
        config.clientId   = props.getProperty (CLIENT_ID);

        if (props.containsKey (RECONNECT)) {
            String text = props.getProperty (RECONNECT);
            try {
                reconnect = Boolean.parseBoolean (text.trim ());
            } catch (Exception ex) {
                logger.warn ("cannot convert {} to boolean.", text.trim ());
                logger.warn (ex.getMessage (), ex);
            }
        }
        config.autoReconnect = reconnect;
        try {
            LOCKER.lock ();
            configs.put (name, config);
        } finally {
            LOCKER.unlock ();
        }
    }

    /**
     * 取消注册一个指定的配置。
     * <p>注意，取消注册一个配置，会导致这个配置下的连接全部断开</p>
     * @param name 指定的配置名称
     */
    @Override
    public void unregister (String name) {
        try {
            LOCKER.lock ();
            if (configs.containsKey (name)) {
                if (managers.containsKey (name)) {
                    // 这个配置已经被连接过了
                    MqttManager manager = managers.get (name);
                    manager.disconnect ();
                }
            }
        } finally {
            LOCKER.unlock ();
        }
    }

    @Override
    public String getServerUrl (String configName) {
        MqttConfig conf = configs.get (configName);
        return conf != null ? conf.url : null;
    }

    private void loadConfigs (IConfiguration conf) {
        Properties props = ((PropertyConfiguration) conf).getRawProperties ();
        Set<String> names = new HashSet<> ();
        for (String name : props.stringPropertyNames ()) {
            Matcher m = PATTERN.matcher (name);
            if (m.matches ()) {
                names.add (m.group (1));
            }
        }

        if (!names.isEmpty ()) {
            for (String name : names) {
                MqttConfig config    = new MqttConfig ();
                config.name          = name;
                config.url           = conf.getString ("mqtt." + name + ".url");
                config.username      = conf.getString ("mqtt." + name + ".user");
                config.password      = conf.getString ("mqtt." + name + ".password");
                config.autoReconnect = conf.getBoolean ("mqtt." + name + ".auto-reconnect", true);
                configs.put (name, config);
            }
        }
    }

    private MqttManager getMqttManager (String name) {
        try {
            LOCKER.lock ();
            if (managers.containsKey (name)) {
                return managers.get (name);
            }

            if (configs.containsKey (name)) {
                MqttConfig mc = configs.get (name);
                MqttManager manager = createManager (mc);
                managers.put (name, manager);
                return manager;
            }
        } finally {
            LOCKER.unlock ();
        }
        return null;
    }

    private MqttManager createManager (MqttConfig config) {
        MqttManager manager = new MqttManager (config, incomingTasks);
//        manager.connect ();
        return manager;
    }

    @Override
    public void dispose () {
        incomingTasks.destroy ();
        outgoingTasks.destroy ();
        managers.values ().forEach (manager -> {
            try {
                manager.disconnect ();
            } catch (Exception ignore) {}
        });
        managers.clear ();

        configs.clear ();
    }
}