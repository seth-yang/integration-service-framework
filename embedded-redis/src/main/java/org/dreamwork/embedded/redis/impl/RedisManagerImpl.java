package org.dreamwork.embedded.redis.impl;

import org.dreamwork.embedded.redis.model.RedisConfig;
import org.dreamwork.integration.api.services.IRedisManager;
import org.dreamwork.integration.api.services.IRedisService;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedisManagerImpl implements IRedisManager {
    private static final Pattern P = Pattern.compile ("^redis\\.(.*?)\\.database\\.(.*?)$");
    private static final Pattern Q = Pattern.compile ("^(.*?)\\.(.*?)$");
    private static final Pattern R = Pattern.compile ("^(.*?)\\s*\\[(\\d+)]$");

    private final Map<String, RedisConfig> pools = new ConcurrentHashMap<> ();

    private final Logger logger = LoggerFactory.getLogger (RedisManagerImpl.class);

    @Override
    public IRedisService getByName (String name) {
        Matcher m = Q.matcher (name);
        if (m.matches ()) {
            return get (m.group (1), m.group (2));
        }
        m = R.matcher (name);
        if (m.matches ()) {
            name = m.group (1);
            String temp = m.group (2);
            try {
                int database = Integer.parseInt (temp);
                return get (name, database);
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
        }
        throw new RuntimeException ("cannot find redis by name: " + name);
    }

    @Override
    public IRedisService get (String name, String databaseName) {
        RedisConfig config = pools.get (name);
        if (config != null) {
            Integer index = config.mapping.get (databaseName);
            if (index != null && index >=0 && index < 16) {
                return config.refs[index];
            }
        }
        throw new RuntimeException ("the redis config: " + name + "." + databaseName +" not exists.");
    }

    @Override
    public IRedisService get (String name, int database) {
        RedisConfig config = pools.get (name);
        if (config != null) {
            if (database >= 0 && database < 16) {
                IRedisService redis = config.refs [database];
                if (redis == null) {
                    config.refs[database] = redis = new RedisServiceImpl (config, database);
                }

                return redis;
            }
        }
        throw new RuntimeException ("the redis config: " + name + "[" + database + "] not exists.");
    }

    @Override
    public void register (String name, Properties props) {
        IConfiguration conf = new PropertyConfiguration (props);
        String host     = conf.getString ("redis." + name + ".host");
        String password = conf.getString ("redis." + name + ".password");
        int expireTime  = conf.getInt ("redis." + name + ".expire.time", 86400);
        int port        = conf.getInt ("redis." + name + ".port", 6379);
        int batchSize   = conf.getInt ("redis." + name + ".scan.batch.size", 1000);

        if (StringUtil.isEmpty (host)) {
            if (logger.isWarnEnabled ()) {
                logger.warn ("the redis config named {} does not exist, check it out", host);
            }
            return;
        }

        RedisConfig config = new RedisConfig (name, host, password, port, expireTime, batchSize);
        pools.put (name, config);
        props.stringPropertyNames ().forEach (key -> {
            Matcher m = P.matcher (key);
            if (m.matches ()) {
                String confName = m.group (1);
                if (name.equals (confName)) {
                    String databaseName = m.group (2);
                    int database = conf.getInt (key, -1);
                    if (database >= 0 && database < 16) {
                        IRedisService redis = new RedisServiceImpl (config, database);
                        config.refs[database] = redis;
                        config.mapping.put (databaseName, database);
                    } else if (logger.isWarnEnabled ()) {
                        logger.warn ("invalid database index: name = {}, database.name = {}, index = {}", key, databaseName, database);
                    }
                }
            }
        });
    }

    @Override
    public void append (String name, String databaseName, int database) {
        if (!pools.containsKey (name)) {
            throw new RuntimeException ("redis config [" + name + "] not exists.");
        }

        RedisConfig config = pools.get (name);
        Integer index = config.mapping.get (databaseName);
        if (index != null && index != database) {
            // 老的和新的不一致，替换掉
            IRedisService redis = config.refs[index];
            if (redis != null) {
                try {
                    redis.dispose ();
                } catch (Throwable ignored) {}
            }
            config.refs[index] = null;
        }
        config.mapping.put (databaseName, database);
        config.refs[database] = new RedisServiceImpl (config, database);
    }

    @Override
    public void delete (String name) {
        if (pools.containsKey (name)) {
            RedisConfig config = pools.remove (name);
            if (config != null) {
                dispose (config);
            }
        }
    }

    @Override
    public void subtract (String name, String databaseName) {
        if (pools.containsKey (name)) {
            RedisConfig config = pools.get (name);
            Integer database = config.mapping.remove (databaseName);
            if (database != null && database >= 0 && database < 16) {
                IRedisService redis = config.refs[database];
                try {
                    redis.dispose ();
                } catch (Throwable ignored) {
                }
                config.refs[database] = null;
            }
        }
    }

    @Override
    public void dispose () {
        pools.values ().forEach (this::dispose);
    }

    private void dispose (RedisConfig config) {
        Arrays.stream (config.refs).forEach (redis -> {
            try {
                redis.dispose ();
            } catch (Throwable ignored) {
            }
        });
        config.mapping.clear ();
    }
}