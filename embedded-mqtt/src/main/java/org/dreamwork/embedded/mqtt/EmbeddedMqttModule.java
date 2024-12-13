package org.dreamwork.embedded.mqtt;

import org.dreamwork.config.IConfiguration;
import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.services.IMqttService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedMqttModule implements IModule {
    private final Logger logger = LoggerFactory.getLogger (EmbeddedMqttModule.class);

    private MqttService mqtt;
    private IModuleContext context;

    @Override
    public void startup (IModuleContext context) throws IntegrationException {
        try {
            this.context = context;
            IConfiguration conf = context.getConfiguration ();
            mqtt = new MqttService (conf);
            context.registerService (IMqttService.class, mqtt);
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new IntegrationException (ex);
        }
    }

    @Override
    public void destroy () throws IntegrationException {
        try {
            if (mqtt != null) {
                mqtt.dispose ();

                if (context != null) {
                    context.unRegisterService (IMqttService.class, mqtt);
                }
            }
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new IntegrationException (ex);
        }
    }
}
