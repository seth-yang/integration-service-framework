package org.dreamwork.embedded.redis;

import org.dreamwork.embedded.redis.impl.RedisManagerImpl;
import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.services.IRedisManager;
import org.dreamwork.config.PropertyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dreamwork.embedded.redis.utils.Function.isNotEmpty;

public class EmbeddedRedisModule implements IModule {
    private final Logger logger;
    private static final Pattern P = Pattern.compile ("^redis\\.(.*?)\\.host$");

    private IRedisManager manager;

    public EmbeddedRedisModule () {
        logger = LoggerFactory.getLogger (EmbeddedRedisModule.class);
    }

    @Override
    public void startup (IModuleContext context) throws IntegrationException {
        PropertyConfiguration pc = (PropertyConfiguration) context.getConfiguration ();
        Set<String> names = new HashSet<> ();
        pc.getRawProperties ().stringPropertyNames ().forEach (key -> {
            Matcher m = P.matcher (key);
            if (m.matches ()) {
                names.add (m.group (1));
            }
        });
        if (logger.isTraceEnabled ()) {
            logger.trace ("parsed redis config name: {}", names);
        }

        manager = new RedisManagerImpl ();
        context.registerService (IRedisManager.class, manager);

        if (isNotEmpty (names)) {
            for (String name : names) {
                manager.register (name, pc.getRawProperties ());
            }
/*
            Map<String, Properties> groups = new HashMap<> ();
            Properties props = pc.getRawProperties ();
            props.stringPropertyNames ().forEach (key -> {
                for (String name : names) {
                    if (key.startsWith ("redis." + name)) {
                        Properties prop = groups.computeIfAbsent (name, k -> new Properties ());
                        prop.setProperty (key, props.getProperty (key));
                        break;
                    }
                }
            });
            if (logger.isTraceEnabled ()) {
                logger.trace ("grouped config = {}", groups);
            }

            groups.forEach (manager::register);
*/
        }
    }

    @Override
    public void destroy () {
        if (manager != null) {
            try {
                manager.dispose ();
            } catch (Throwable t) {
                logger.warn (t.getMessage (), t);
            }
        }
    }

    @Override
    public String getMBeanName () {
        return null;
    }
}