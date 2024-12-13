package org.dreamwork.embedded.mqtt;

import org.dreamwork.integration.api.services.IMqttListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;

class MqttCallbackDelegate implements MqttCallbackExtended {
    /**
     * topic和listener对应map
     */
    private final Map<String, List<IMqttListener>> listenerMap = new HashMap<> ();
    private final ExecutorService executor;

    private final Logger logger = LoggerFactory.getLogger (MqttCallbackDelegate.class);

    public MqttCallbackDelegate (ExecutorService executor) {
        this.executor = executor;
    }

    public void addListener (String topic, IMqttListener... mqttListeners) {
        List<IMqttListener> iMqttListeners = listenerMap.computeIfAbsent (topic, key -> new ArrayList<> ());
        iMqttListeners.addAll (Arrays.asList (mqttListeners));
    }

    public void removeAllListener () {
        listenerMap.clear ();
    }

    public void removeListener (String topic, IMqttListener... mqttListeners) {
        List<IMqttListener> iMqttListeners = listenerMap.get (topic);
        iMqttListeners.removeAll (Arrays.asList (mqttListeners));
        if (iMqttListeners.isEmpty ()) {
            listenerMap.remove (topic);
        }
    }


    @Override
    public void connectComplete (boolean reconnect, String serverURI) {
        try {
            for (List<IMqttListener> value : listenerMap.values ()) {
                for (IMqttListener listener : value) {
                    listener.onConnected (serverURI);
                }
            }
        } catch (Throwable t) {
            logger.warn (t.getMessage (), t);
        }
    }

    @Override
    public void connectionLost (Throwable cause) {
        try {
            for (List<IMqttListener> value : listenerMap.values ()) {
                for (IMqttListener listener : value) {
                    listener.onError (cause);
                }
            }
        } catch (Throwable t) {
            logger.warn (t.getMessage (), t);
        }
    }

    @Override
    public void messageArrived (String topic, MqttMessage message) throws Exception {
        executor.execute (() -> {
            try {
                byte[] buff = null;
                if (message != null && message.getPayload () != null) {
                    buff = message.getPayload ();
                }
                for (String s : listenerMap.keySet ()) {
                    if (s.equals (topic)) {
                        for (IMqttListener listener : listenerMap.get (s)) {
                            listener.onMessage (topic, buff);
                        }
                    } else {
                        if (s.contains ("#") || s.contains ("+")) {
                            boolean success = true;
                            String[] split = s.split ("/");
                            String[] topicSplit = topic.split ("/");
                            for (int i = 0; i < split.length; i++) {
                                if ("#".equals (split[i])) {
                                    break;
                                }
                                if (!split[i].equals (topicSplit[i])) {
                                    if (!"+".equals (split[i])) {
                                        success = false;
                                        break;
                                    }
                                }
                            }
                            if (success) {
                                for (IMqttListener listener : listenerMap.get (s)) {
                                    listener.onMessage (topic, buff);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
        });
    }

    @Override
    public void deliveryComplete (IMqttDeliveryToken token) {
        try {
            for (List<IMqttListener> value : listenerMap.values ()) {
                for (IMqttListener listener : value) {
                    listener.onSend (((Message) token.getMessage ()).topic, token.getMessage ().getPayload ());
                }
            }
        } catch (MqttException ex) {
            logger.warn (ex.getMessage (), ex);
        }
    }
}