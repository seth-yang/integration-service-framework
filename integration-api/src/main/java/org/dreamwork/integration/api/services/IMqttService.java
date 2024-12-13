package org.dreamwork.integration.api.services;

import java.util.Properties;

/**
 * Created by seth.yang on 2019/12/12
 */
public interface IMqttService {

    /**
     * 推送消息
     * @param configName mqtt配置名称
     * @param topic      主题
     * @param content    内容
     */
    void publish (String configName, String topic, byte[] content);

    /**
     * 订阅消息
     * @param configName mqtt配置名称
     * @param topic      主题
     * @param listener   监听器
     */
    void subscribe (String configName, String topic, IMqttListener ... listener);

    void unsubscribe (String configName, String... topic);

    /**
     * 获取所有有效的配置名称
     *
     * @return 所有有效配置名称的迭代器
     */
    Iterable<String> getAllNames ();

    void register (String name, Properties props);

    void unregister (String name);

    String getServerUrl (String configName);
}
