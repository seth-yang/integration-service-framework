package org.dreamwork.embedded.mqtt.data;

import org.dreamwork.integration.api.services.IMqttListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.Serializable;

public class IncomingMessage implements Serializable {
    public String topic;
    public MqttMessage message;

    public IMqttListener listener;

    public IncomingMessage (String topic, MqttMessage message, IMqttListener listener) {
        this.topic = topic;
        this.message = message;
        this.listener = listener;
    }
}