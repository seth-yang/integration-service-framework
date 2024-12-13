package org.dreamwork.embedded.mqtt;

import org.dreamwork.embedded.mqtt.data.IncomingMessage;
import org.dreamwork.embedded.mqtt.data.MqttConfig;
import org.dreamwork.concurrent.TaskGroup;
import org.dreamwork.integration.api.services.IMqttListener;
import org.dreamwork.util.StringUtil;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * MQTT 的管理类
 *
 */
public class MqttManager implements MqttCallback {
    private MqttClient client;
//    private MqttCallbackDelegate delegate;

    private final MqttConfig conf;
    private final Logger logger = LoggerFactory.getLogger (MqttManager.class);
    private final Map<String, IMqttListener[]> subscribedTopics = new HashMap<> ();
    private final TaskGroup<IncomingMessage> group;
    private String clientId;
    private MqttConnectOptions opts;
    private static final int QOS_LEVEL = 0;

    MqttManager (MqttConfig conf, TaskGroup<IncomingMessage> group) {
        this.conf  = conf;
        this.group = group;
    }

    private String generateClientId () {
        clientId = conf.clientId;
        if (!StringUtil.isEmpty (clientId)) {
            if (clientId.contains ("${")) {
                Pattern p = Pattern.compile ("\\$\\{(.*?)\\}", Pattern.MULTILINE);
                Matcher m = p.matcher (clientId);
                if (m.matches ()) {
                    String key = m.group (1);
                    StringBuffer buffer = new StringBuffer ();
                    switch (key.trim ()) {
                        case "uuid":
                            m.appendReplacement (buffer, StringUtil.uuid ());
                            break;

                        case "timestamp":
                            m.appendReplacement (buffer, String.valueOf (System.currentTimeMillis ()));
                            break;

                        case "random":
                            SecureRandom sr = new SecureRandom ();
                            DecimalFormat df = new DecimalFormat ("00000000");
                            m.appendReplacement (buffer, df.format (sr.nextInt (99999999)));
                            break;

                        default:
                            m.appendReplacement (buffer, key.trim ());
                            break;
                    }
                    m.appendTail (buffer);
                    clientId = buffer.toString ();
                }
            }
        } else {
            clientId = StringUtil.uuid ();
        }

        return clientId;
    }

    public void connect () {
        if (conf == null || StringUtil.isEmpty (conf.url)) {
            throw new RuntimeException ("cannot connect to empty url. please ensure that the mqtt url has been set correctly!");
        }
        synchronized (conf) {
            if (clientId == null) {
                clientId = generateClientId ();
            }

            try {
                if (client == null || !client.isConnected ()) {
                    client = new MqttClient (conf.url, clientId);
                    client.setTimeToWait (30000L);
                    opts = new MqttConnectOptions ();
                    opts.setAutomaticReconnect (true);
                    opts.setCleanSession (false);
                    if (conf.timeout != null) {
                        opts.setConnectionTimeout (conf.timeout);
                    }
                    if (conf.interval != null) {
                        opts.setKeepAliveInterval (conf.interval);
                    }
                    if (!StringUtil.isEmpty (conf.username)) {
                        opts.setUserName (conf.username);
                    }
                    if (!StringUtil.isEmpty (conf.password)) {
                        opts.setPassword (conf.password.toCharArray ());
                    }
                    client.setManualAcks (false);
                    client.setCallback (this);
                }

                client.connect (opts);
            } catch (MqttException ex) {
                logger.warn (ex.getMessage (), ex);
                throw new RuntimeException (ex);
            }
        }
    }

//    public void connect (String url, IMqttListener listener) {
//        connect (url, null, null);
//    }
//
//    public void setListener (String topic, IMqttListener... mqttListeners) {
//        delegate.addListener (topic, mqttListeners);
//    }
//
//    public void connect (String url, String user, String password) {
//        if (client == null) {
//            if (logger.isDebugEnabled ()) {
//                logger.debug ("trying to connect to " + url + "...");
//            }
//            String id = UUID.randomUUID ().toString ();
//            try {
//                client = new MqttClient (url, id);
//                client.setTimeToWait (30000L);
//                MqttConnectOptions opts = new MqttConnectOptions ();
//                opts.setAutomaticReconnect (true);
//                opts.setCleanSession (false);
//                if (user != null) {
//                    opts.setUserName (user);
//                }
//                if (password != null) {
//                    opts.setPassword (password.toCharArray ());
//                }
//                client.setManualAcks (false);
//                delegate = new MqttCallbackDelegate (executor);
//                client.setCallback (this);
//                client.connect (opts);
//
//                if (!subscribedTopics.isEmpty ()) {
//                    subscribedTopics.forEach (this::subscribe);
//                }
//            } catch (MqttException ex) {
//                throw new RuntimeException (ex);
//            }
//        } else {
//            if (logger.isDebugEnabled ()) {
//                logger.debug ("the client has connected.");
//            }
//        }
//    }

    public void subscribe (String topic, IMqttListener... listeners) {
        if (client == null) {
            throw new IllegalStateException ("the client is not connected.");
        }
        if (StringUtil.isEmpty (topic)) {
            throw new IllegalStateException ("empty topic is not supported.");
        }

//        boolean success = true;
        List<IMqttListener> cache = new ArrayList<> ();
        for (IMqttListener listener : listeners) {
            MqttMessageListenerDelegate delegate = new MqttMessageListenerDelegate (group, listener);
            try {
                client.subscribe (topic, delegate);
                cache.add (listener);
            } catch (MqttException ex) {
                logger.warn (ex.getMessage (), ex);
//                success = false;
            }
        }
        if (!cache.isEmpty ()) {
            subscribedTopics.put (topic, cache.toArray (new IMqttListener[0]));
        }
    }

    public void unsubscribe (String... topics) {
        if (client != null) {
            try {
                client.unsubscribe (topics);
            } catch (MqttException ignore) {}
            finally {
                for (String topic : topics) {
                    subscribedTopics.remove (topic);
                }

                // 再也没有被订阅的主题了，先断开连接
                if (subscribedTopics.isEmpty ()) {
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("there's no more topic subscribed, disconnect from mqtt broker");
                    }
                    try {
                        client.disconnect ();
                    } catch (MqttException ex) {
                        if (logger.isTraceEnabled ()) {
                            logger.warn (ex.getMessage (), ex);
                        }
                    }
                }
            }
        }
    }

//    public void subscribe (String... topics) {
//        if (client == null) {
//            throw new IllegalStateException ("the client is not connected.");
//        }
//        if (topics != null && topics.length > 0) {
//            try {
////                client.subscribe (topics);
//                for (String topic : topics) {
//                    client.subscribe (topic, new IMqttMessageListener () {
//                        @Override
//                        public void messageArrived (String topic, MqttMessage message) throws Exception {
//
//                        }
//                    });
//                }
//            } catch (MqttException ex) {
//                throw new RuntimeException (ex);
//            }
//        }
//    }

//    public void send (String topic, String content) {
//        byte[] buff;
//        if (StringUtil.isEmpty (content)) {
//            buff = new byte[]{};
//        } else {
//            buff = content.trim ().getBytes (StandardCharsets.UTF_8);
//        }
//        send (topic, buff);
//    }
//
//
//    public void send (String topic, Message message) {
//        if (logger.isTraceEnabled ()) {
//            logger.trace ("sending message to topic: " + topic + " ");
//        }
//        if (client == null) {
//            throw new IllegalStateException ("the client is not connected.");
//        }
//        try {
//            client.publish (topic, message);
//            if (logger.isDebugEnabled ()) {
//                logger.debug ("the message sent.");
//            }
//        } catch (MqttException ex) {
//            throw new RuntimeException (ex);
//        }
//    }

    public void send (String topic, byte[] data) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("sending message to topic: " + topic + ", message: ");
            logger.trace (StringUtil.format (data));
        }

        if (client == null) {
            throw new IllegalStateException ("the client is not connected.");
        }

        try {
            client.publish (topic, data, QOS_LEVEL, false);
            if (logger.isDebugEnabled ()) {
                logger.debug ("the message sent.");
            }
        } catch (MqttException ex) {
            throw new RuntimeException (ex);
        }
    }

    public boolean isConnected () {
        return client != null && client.isConnected ();
    }

    public boolean isDisconnected () {
        return client == null || !client.isConnected ();
    }

    public void disconnect () {
        try {
            if (client != null) {
                // 取消所有订阅
                client.unsubscribe (subscribedTopics.keySet ().toArray (new String[0]));
                // 挥手
                client.disconnect ();
            }
            client = null;
            subscribedTopics.clear ();
            logger.info ("MqttManager shutdown");
//            delegate.removeAllListener ();
        } catch (MqttException ignore) {}
    }

    @Override
    public void connectionLost (Throwable cause) {
        // 啥也不用干，因为 autoReconnect 设置为 true 了
/*
        logger.warn ("the mqtt[{}] lost its connection.", conf.name);
        if (conf.autoReconnect) {
            Map<String, IMqttListener[]> copy = null;
            if (!subscribedTopics.isEmpty ()) {
                copy = new HashMap<> (subscribedTopics);
            }
            disconnect ();

            if (copy != null && !copy.isEmpty ()) {
                subscribedTopics.clear ();
                subscribedTopics.putAll (copy);
            }
            connect ();
        }
*/
    }

    @Override
    public void messageArrived (String topic, MqttMessage message) {}

    @Override
    public void deliveryComplete (IMqttDeliveryToken token) {}
}