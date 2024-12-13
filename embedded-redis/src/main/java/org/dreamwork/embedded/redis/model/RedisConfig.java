package org.dreamwork.embedded.redis.model;

import org.dreamwork.integration.api.services.IRedisService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RedisConfig implements Serializable {
    public String name, host, password;
    public int port, expireTime, scanBatchSize;
    public Map<String, Integer> mapping = new HashMap<> ();
    public IRedisService[] refs = new IRedisService[16];

    public RedisConfig (String name, String host, String password, int port, int expireTime, int scanBatchSize) {
        this.name = name;
        this.host = host;
        this.password = password;
        this.port = port;
        this.expireTime = expireTime;
        this.scanBatchSize = scanBatchSize;
    }

    public RedisConfig copy () {
        return new RedisConfig (name, host, password, port, expireTime, scanBatchSize);
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass () != o.getClass ()) return false;
        RedisConfig that = (RedisConfig) o;
        return Objects.equals (name, that.name);
    }

    @Override
    public int hashCode () {
        return Objects.hash (name);
    }
}