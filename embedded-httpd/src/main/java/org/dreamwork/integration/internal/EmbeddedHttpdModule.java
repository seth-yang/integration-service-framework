package org.dreamwork.integration.internal;

import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.services.IHttpdService;
import org.dreamwork.config.IConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by seth.yang on 2019/12/5
 */
public class EmbeddedHttpdModule implements IModule {
    private final Logger logger = LoggerFactory.getLogger (EmbeddedHttpdModule.class);

    private EmbeddedHttpdService httpd;
    private IModuleContext context;

    @Override
    public void startup (IModuleContext context) throws IntegrationException {
        try {
            this.context = context;
            IConfiguration conf = context.getConfiguration ();
            httpd = new EmbeddedHttpdService (conf);
            httpd.start ();
            context.registerService (IHttpdService.class, httpd);
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new IntegrationException (ex);
        }
    }

    @Override
    public void destroy () throws IntegrationException {
        try {
            if (httpd != null) {
                httpd.shutdown ();

                if (context != null) {
                    context.unRegisterService (IHttpdService.class, httpd);
                }
            }
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new IntegrationException (ex);
        }
    }

    @Override
    public String getMBeanName () {
        return null;
    }
}
