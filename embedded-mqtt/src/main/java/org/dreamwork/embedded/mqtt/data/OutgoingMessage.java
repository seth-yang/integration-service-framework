package org.dreamwork.embedded.mqtt.data;

import org.dreamwork.embedded.mqtt.MqttManager;

public class OutgoingMessage {
    public String topic;
    public byte[] content;
    public MqttManager manager;

    public OutgoingMessage (String topic, byte[] content, MqttManager manager) {
        this.topic = topic;
        this.content = content;
        this.manager = manager;
    }
}
