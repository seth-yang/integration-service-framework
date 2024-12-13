package org.dreamwork.embedded.mqtt;

import org.dreamwork.embedded.mqtt.data.IncomingMessage;
import org.dreamwork.concurrent.TaskGroup;
import org.dreamwork.integration.api.services.IMqttListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

class MqttMessageListenerDelegate implements IMqttMessageListener {
    private final Logger logger = LoggerFactory.getLogger (MqttMessageListenerDelegate.class);
    private final IMqttListener stub;
    private final TaskGroup<IncomingMessage> group;

    MqttMessageListenerDelegate (TaskGroup<IncomingMessage> group, IMqttListener stub) {
        Objects.requireNonNull (stub);
        this.stub = stub;
        this.group = group;
    }

    @Override
    public void messageArrived (String topic, MqttMessage message) {
        if (!group.offer (new IncomingMessage (topic, message, stub))) {
            logger.warn ("cannot offer message of topic: {}", topic);
        }
    }
}