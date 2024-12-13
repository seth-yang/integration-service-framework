package org.dreamwork.embedded.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Message extends MqttMessage {

    public Message(String topic, byte[] payload) {
        super(payload);
        this.topic = topic;
    }

    String topic;
}
