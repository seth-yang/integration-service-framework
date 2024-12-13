package org.dreamwork.integration.api.services;

/**
 * Created by seth.yang on 2019/12/12
 */
public interface IMqttListener {
    /**
     * 当成功连接上mqtt服务器后触发
     *
     * @param url mqtt 服务器url
     */
    default void onConnected (String url) {
        System.out.println ("connect to " + url + " successful.");
    }

    /**
     * 当mqtt操作过程中任何错误发生时触发
     *
     * @param cause 错误原因
     */
    default void onError (Throwable cause) {
        cause.printStackTrace ();
    }

    /**
     * 当接收到一个消息时触发程序
     *
     * @param topic   主题
     * @param content 内容
     */
    void onMessage (String topic, byte[] content);

    /**
     * 当成功发送消息后触发
     *
     * @param topic 主题
     * @param data  内容
     */
    void onSend (String topic, byte[] data);
}
