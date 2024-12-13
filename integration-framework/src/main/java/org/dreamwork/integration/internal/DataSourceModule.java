package org.dreamwork.integration.internal;

import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.services.IDatabaseService;
import org.dreamwork.integration.services.impl.DataSourceService;
import org.dreamwork.integration.util.Helper;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class DataSourceModule implements IModule {
    private IModuleContext context;
    private IDatabaseService service;

    private final Logger logger = LoggerFactory.getLogger (DataSourceModule.class);

    @Override
    public void startup (IModuleContext context) throws IntegrationException {
        this.context = context;
        service = new DataSourceService ();
        context.registerService (IDatabaseService.class, service);
        if (logger.isTraceEnabled ()) {
            logger.trace ("database service registered as {}", service);
        }

        PropertyConfiguration pc = (PropertyConfiguration) context.getConfiguration ();

        if (pc != null && !pc.getRawProperties ().isEmpty ()) {
            Properties props = pc.getRawProperties ();
            String configName = props.getProperty ("db.pool.name");
            if (!StringUtil.isEmpty (configName)) {
                Helper.loadJDBCConfig (service, props, configName);
            }
        }
    }

    @Override
    public String getMBeanName () {
        return null;
    }

   @Override
    public void destroy () {
        if (context != null) {
            context.unRegisterService (IDatabaseService.class, service);
        }
    }
}