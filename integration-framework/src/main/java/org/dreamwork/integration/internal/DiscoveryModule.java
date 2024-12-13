package org.dreamwork.integration.internal;

import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.udp.Broadcaster;
import org.dreamwork.network.udp.MagicData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscoveryModule implements IModule {
    private ExecutorService executor;
    private Broadcaster broadcaster;

    @Override
    public void startup (IModuleContext context) throws IntegrationException {
        IConfiguration configuration = context.getConfiguration ();
        int port = configuration.getInt ("discovery.service.port", 9000);
        MagicData magic = new MagicData ("!!YueMa??".getBytes (), "Yue!!".getBytes ());
        executor = Executors.newFixedThreadPool (8);
        broadcaster = new Broadcaster ();
        broadcaster.setExecutor (executor);
        broadcaster.setMagic (magic);
        try {
            broadcaster.bind (port);
        } catch (Exception ex) {
            throw new IntegrationException (ex);
        }
    }

    @Override
    public void destroy () {
        if (broadcaster != null) {
            broadcaster.unbind ();
        }
        if (executor != null) {
            executor.shutdownNow ();
        }
    }

    @Override
    public String getMBeanName () {
        return null;
    }
}
