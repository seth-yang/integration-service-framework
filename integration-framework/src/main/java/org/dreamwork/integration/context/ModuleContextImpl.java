package org.dreamwork.integration.context;

import org.dreamwork.integration.api.*;
import org.dreamwork.integration.api.services.IDatabaseService;
import org.dreamwork.integration.api.services.IHttpdService;
import org.dreamwork.integration.api.services.IMqttService;
import org.dreamwork.integration.proxy.ServiceProxyFactory;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class ModuleContextImpl implements IModuleContext {
    private ModuleInfo info;
    private PropertyConfiguration configuration;
    private IModule instance;
    private IMBeanDelegator mBeanDelegation;
    private SimpleServiceRouter serviceRouter;
    private IModuleContextHandler handler;
    private ClassLoader classLoader;
    private String name;
    private Path workdir;   // ../work/${moduleName}

    private final Set<String> dbConfigs   = new HashSet<> ();
    private final Set<String> mqttConfigs = new HashSet<> ();
    Set<ClassScanner.Wrapper> wrappers = new HashSet<> ();

    private final Logger logger = LoggerFactory.getLogger (ModuleContextImpl.class);

    void setMbeanDelegation (IMBeanDelegator delegation) {
        this.mBeanDelegation = delegation;
    }

    void setConfiguration (PropertyConfiguration configuration) {
        this.configuration = configuration;
    }

    void setServiceRouter (SimpleServiceRouter router) {
        this.serviceRouter = router;
    }

    SimpleServiceRouter getServiceRouter () {
        return serviceRouter;
    }

    void setInstance (IModule instance) {
        this.instance = instance;
    }

    IModule getInstance () {
        return instance;
    }

    void setName (String name) {
        this.name = name;
    }

    void setClassLoader (ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    void setWorkdir (Path workdir) {
        this.workdir = workdir;
    }

    Path getWorkdir () {
        return workdir;
    }

    public ModuleInfo getInfo () {
        return info;
    }

    void setInfo (ModuleInfo info) {
        this.info = info;
    }

    @Override
    public String getName () {
        return name;
    }

    public IModuleContextHandler getHandler () {
        return handler;
    }

    void setHandler (IModuleContextHandler handler) {
        this.handler = handler;
    }

    @Override
    public <T> T findService (String name) {
        return serviceRouter == null ? null : serviceRouter.findService (name);
    }

    @Override
    @SuppressWarnings ("all")
    public <T> T findService (Class<T> type) {
        Object o = serviceRouter.findService (type);
        if (o == null) {
            return null;
        }
        Class<T> c = (Class<T>) o.getClass ();
        if (c.getClassLoader () == type.getClassLoader ()) {
            return (T) o;
        }

        return ServiceProxyFactory.createProxy (classLoader, o, type);
    }

    @Override
    public void registerService (Class<?> type, Object object) {
        if (serviceRouter != null)
            serviceRouter.registerService (this, type, object);
    }

    @Override
    public void registerService (String name, Object bean) {
        if (serviceRouter != null) {
            serviceRouter.registerService (this, name, bean);
        }
    }

    @Override
    public <T> void unRegisterService (Class<T> type, T object) {
        if (serviceRouter != null) {
            serviceRouter.unregisterService (type, object);
        }
    }

    @Override
    public IConfiguration getConfiguration () {
        return configuration;
    }

    @Override
    public void registerMBean (String name, Object instance) throws Exception {
        if (mBeanDelegation != null) {
            mBeanDelegation.registerMBean (name, instance);
        }
    }

    @Override
    public void unregisterMBean (String name) throws Exception {
        if (mBeanDelegation != null) {
            mBeanDelegation.unregisterMBean (name);
        }
    }

    @Override
    public ClassLoader getContextClassLoader () {
        return classLoader;
    }

    @Override
    public URL getResource (String name) {
        URL url = classLoader != null ? classLoader.getResource (name) : null;
        if (url == null && workdir != null) {
            Path path = Paths.get (workdir.toString (), name);
            if (Files.exists (path)) {
                try {
                    return path.toUri ().toURL ();
                } catch (Exception ex) {
                    // nothing to do
                }
            }
        }
        return url;
    }

    @Override
    public void addModuleListener (IModuleListener listener) {
        if (handler != null) {
            handler.addModuleListener (listener);
        }
    }

    @Override
    public void removeModuleListener (IModuleListener listener) {
        if (handler != null) {
            handler.removeModuleListener (listener);
        }
    }

    /**
     * 销毁模块容器实例
     */
    void destroy () throws IntegrationException {
        {   // 清除restful api
            IHttpdService service = findService (IHttpdService.class);
            if (service != null) {
                service.detach (info);
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("http service cleared in module: [{}]", info.name);
            }
        }

        if (this.wrappers != null && !this.wrappers.isEmpty ()) {
            for (ClassScanner.Wrapper w : wrappers) {
                if (w.preDestroy != null) {
                    try {
                        w.preDestroy.invoke (w.bean);
                    } catch (Exception ex) {
                        if (logger.isTraceEnabled ()) {
                            logger.warn (ex.getMessage (), ex);
                        }
                    }
                }
            }
        }

        if (instance != null) {
            instance.destroy ();
        }

        serviceRouter.clean (this);

        if (!dbConfigs.isEmpty ()) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("cleaning registered jdbc datasource...");
            }
            IDatabaseService service = findService (IDatabaseService.class);
            if (service != null) {
                for (String name : dbConfigs) {
                    service.unregister (name);
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("jdbc config [{}] cleared", name);
                    }
                }
            }
        }

        if (!mqttConfigs.isEmpty ()) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("cleaning registered mqtt connections");
            }
            IMqttService service = findService (IMqttService.class);
            if (service != null) {
                for (String name : mqttConfigs) {
                    service.unregister (name);

                    if (logger.isTraceEnabled ()) {
                        logger.trace ("mqtt config [{}] cleared.", name);
                    }
                }
            }
        }

        if (classLoader instanceof ModuleClassLoader) {
            ((ModuleClassLoader) classLoader).destroy ();
        }

        workdir  = null;
        instance = null;
    }

    @Override
    public String toString () {
        return name;
    }

    void addDatabaseConfig (String name) {
        dbConfigs.add (name);
    }

    void addMqttConfig (String name) {
        mqttConfigs.add (name);
    }
}