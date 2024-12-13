package org.dreamwork.embedded.redis.impl;

import org.dreamwork.embedded.redis.model.RedisConfig;
import org.dreamwork.integration.api.services.IRedisService;
import org.dreamwork.util.StringUtil;
import redis.clients.jedis.*;

import java.util.*;

public class RedisServiceImpl implements IRedisService {
    private final JedisPool pool;
    private final int scanBatchSize;

    RedisServiceImpl (RedisConfig config, int database) {
        this (config.host, config.password, config.port, config.expireTime, database, config.scanBatchSize);
    }

    RedisServiceImpl (String host, String password, int port, int expireTime, int database, int scanBaseSize) {
        JedisPoolConfig conf = new JedisPoolConfig ();
        pool = new JedisPool (conf, host, port, expireTime, password, database);
        this.scanBatchSize = scanBaseSize;
    }

    @Override
    public void writeMap (String key, Map<String, String> map) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.hmset (key, map);
        }
    }

    @Override
    public void writeMaps (String[] keys, Map<String, String>[] maps) {
        if (keys == null || maps == null) {
            throw new NullPointerException ();
        }
        if (keys.length != maps.length) {
            throw new IllegalArgumentException ();
        }
        try (Jedis jedis = pool.getResource ()) {
            for (int i = 0; i < keys.length; i ++) {
                jedis.hmset (keys[i], maps[i]);
            }
        }
    }

    @Override
    public String[] readMapValues (String key, String... fields) {
        String[] values = new String[fields.length];
        try (Jedis jedis = pool.getResource ()) {
            List<String> data = jedis.hmget (key, fields);
            if (data != null && !data.isEmpty ()) {
                for (int i = 0; i < data.size (); i ++) {
                    String value = data.get (i);
                    if (!StringUtil.isEmpty (value)) {
                        values[i] = value.trim ();
                    }
                }
            }
        }
        return values;
    }

    @Override
    public Map<String, String> readAsMap (String key, String... fields) {
        try (Jedis jedis = pool.getResource ()) {
            return readAsMap (jedis, key, fields);
        }
    }

    @Override
    public String readValue (String key, String field) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.hget (key, field);
        }
    }

    @Override
    public void writeValue (String key, String field, String value) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.hset (key, field, value);
        }
    }

    @Override
    public void delete (String... keys) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.del (keys);
        }
    }

    @Override
    public void deleteField (String key, String... fields) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.hdel (key, fields);
        }
    }

    @Override
    public boolean present (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.exists (key);
        }
    }

    @Override
    public boolean present (String key, String field) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.hexists (key, field);
        }
    }

    public boolean matches (String pattern) {
        try (Jedis jedis = pool.getResource ()) {
            ScanParams sp = new ScanParams ().match (pattern).count (100);
            ScanResult<String> result = jedis.scan ("0", sp);
            if ("0".equals (result.getCursor ())) {
                List<String> list = result.getResult ();
                return list != null && !list.isEmpty ();
            }
            return true;
        }
    }

    public boolean matches (String key, String pattern) {
        try (Jedis jedis = pool.getResource ()) {
            ScanParams sp = new ScanParams ().match (pattern).count (100);
            ScanResult<Map.Entry<String, String>> result = jedis.hscan (key, "0", sp);
            if ("0".equals (result.getCursor ())) {
                List<Map.Entry<String, String>> list = result.getResult ();
                return list != null && !list.isEmpty ();
            }
            return true;
        }
    }

    public List<String> queryKeys (String pattern) {
        try (Jedis jedis = pool.getResource ()) {
            ScanParams sp = new ScanParams ().match (pattern).count (scanBatchSize);
            List<String> buff = new ArrayList<> ();

            ScanResult<String> result = jedis.scan ("0", sp);
            String cursor;
            do {
                cursor = result.getCursor ();
                List<String> list = result.getResult ();
                if (list != null && !list.isEmpty ()) {
                    buff.addAll (list);
                }
                if (!"0".equals (cursor)) {
                    result = jedis.scan (cursor, sp);
                }
            } while (!"0".equals (cursor));
            return buff;
        }
    }

    public Map<String, String> query (String pattern) {
        List<String> keys = queryKeys (pattern);
        if (!keys.isEmpty ()) {
            try (Jedis jedis = pool.getResource ()) {
                List<String> list = jedis.mget (keys.toArray (new String[0]));
                if (list != null && !list.isEmpty ()) {
                    int n = keys.size ();
                    Map<String, String> map = new HashMap<> (n);
                    for (int i = 0; i < n; i ++) {
                        map.put (keys.get (i), list.get (i));
                    }
                    return map;
                }
            }
        }
        return Collections.emptyMap ();
    }

    public Map<String, String> query (String key, String pattern) {
        try (Jedis jedis = pool.getResource ()) {
            return query (jedis, key, pattern);
        }
    }

    public Map<String, Map<String, String>> queryAsMaps (String pattern) {
        List<String> keys = queryKeys (pattern);
        if (!keys.isEmpty ()) {
            Map<String, Map<String, String>> map = new HashMap<> (keys.size ());
            try (Jedis jedis = pool.getResource ()) {
                for (String key : keys) {
                    map.put (key, readAsMap (jedis, key));
                }
            }
            return map;
        }

        return Collections.emptyMap ();
    }

    /**
     * 设置一个key的值的存活相对时间，单位为{@code 秒}
     * @param key             键值
     * @param offsetInSeconds 存活的相对时间，秒
     * @return 若设置成功返回 true，否则返回 false
     */
    @Override
    public boolean setExpiredIn (String key, int offsetInSeconds) {
        try (Jedis jedis = pool.getResource ()) {
            Long code = jedis.expire (key, offsetInSeconds);
            return code != null && code > 0;
        }
    }

    /**
     * 删除一个键的过期设置. 既将指定的 key 转成永久存储
     *
     * @param key 键值
     * @return 删除成功返回 true，否则返回 false
     */
    @Override
    public boolean removeExpiration (String key) {
        try (Jedis jedis = pool.getResource ()) {
            Long code = jedis.persist (key);
            return code != null && code > 0;
        }
    }

    /**
     * 获取一个键的剩余存活时间
     *
     * @param key 键
     * @return 若指定的 {@code key} 没有设置过超时时间，返回返回 -1；若键不存在或已经过期，返回 -2，否则返回键的剩余存活时间 ({@code 毫秒}
     */
    @Override
    public long getLifetime (String key) {
        return 0;
    }

    /**
     * 设置一个 key 的存活绝对时间戳，单位 {@code 毫秒}
     * @param key       键
     * @param timestamp 绝对时间， java timestamp
     * @return 若设置成功返回 true，否则返回 false
     */
    @Override
    public boolean setExpiredAt (String key, long timestamp) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.expireAt (key, timestamp);
            Long code = jedis.pexpireAt (key, timestamp);
            return code != null && code > 0;
        }
    }

    /**
     * 向 redis 中写入一个键值对
     *
     * @param key   键
     * @param value 值
     */
    @Override
    public void set (String key, String value) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.set (key, value);
        }
    }

    /**
     * 从 redis 中获取指定 key 的值
     *
     * @param key 键
     * @return key 对应的值
     */
    @Override
    public String get (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.get (key);
        }
    }

    /**
     * 设置一个具有存活时间({@code 毫秒})的键值对
     * @param key     键
     * @param value   值
     * @param timeout 超时相对时间，毫秒
     */
    @Override
    public void setValueWithTimeout (String key, String value, long timeout) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.psetex (key, timeout, value);
        }
    }

    /**
     * 在{@code key}指定的列表右侧压入一个值
     *
     * @param key    列表键名
     * @param values 要压入的值
     */
    @Override
    public void push (String key, String... values) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.rpush (key, values);
        }
    }

    /**
     * 弹出{@code key}指定的列表的<strong>最左边</strong>的元素
     *
     * @param key 列表的键名
     * @return 最左侧的元素
     */
    @Override
    public String take (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.lpop (key);
        }
    }

    /**
     * 弹出{@code key}指定的列表<strong>最右侧</strong>的元素
     *
     * @param key 列表的键名
     * @return 最右侧的元素
     */
    @Override
    public String pop (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.rpop (key);
        }
    }

    /**
     * 获取由{@code key}指定的列表的长度
     *
     * @param key 列表的键名
     * @return 列表的长度
     */
    @Override
    public long getListLength (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.llen (key);
        }
    }

    /**
     * 更新由{@code key}指定的列表中，由{@code index}位置上的元素值
     *
     * @param key   列表的键名
     * @param index 列表中的位置索引
     * @param value 新的值
     */
    @Override
    public void setListItem (String key, int index, String value) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.lset (key, index, value);
        }
    }

    /**
     * 获取由{@code key}指定的列表中，从{@code start}到{@code end}的子列表
     *
     * @param key   列表的键名
     * @param start 开始下标
     * @param end   结束下标
     * @return 子列表
     */
    @Override
    public List<String> sublist (String key, int start, int end) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.lrange (key, start, end);
        }
    }

    /**
     * 获取由{@code key}指定的列表中由{@code index}索引的元素
     *
     * @param key   列表的键值
     * @param index 列表内的索引
     * @return 指定的值
     */
    @Override
    public String getItem (String key, int index) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.lindex (key, index);
        }
    }

    //////////////////////////// 集合操作 //////////////////////////
    @Override
    public void addSetItem (String key, String... values) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.sadd (key, values);
        }
    }

    @Override
    public void removeSetItem (String key, String... values) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.srem (key, values);
        }
    }

    @Override
    public boolean isInSet (String key, String value) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.sismember (key, value);
        }
    }

    @Override
    public long getSetSize (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.scard (key);
        }
    }

    @Override
    public Set<String> getMembers (String key) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.smembers (key);
        }
    }

    @Override
    public double increase (String key, String member) {
        try (Jedis jedis = pool.getResource ()) {
            return jedis.zincrby (key, 1, member);
        }
    }

    @Override
    public void resetCounter (String key, String member) {
        try (Jedis jedis = pool.getResource ()) {
            jedis.zadd (key, 0, member);
        }
    }

    private Map<String, String> query (Jedis jedis, String key, String pattern) {
        ScanParams sp = new ScanParams ().match (pattern).count (scanBatchSize);
        Map<String, String> map = new HashMap<> ();

        ScanResult<Map.Entry<String, String>> result = jedis.hscan (key, "0", sp);
        String cursor;
        do {
            cursor = result.getCursor ();
            List<Map.Entry<String, String>> list = result.getResult ();
            if (list != null && !list.isEmpty ()) {
                list.forEach (e -> map.put (e.getKey (), e.getValue ()));
            }
            if (!"0".equals (cursor)) {
                result = jedis.hscan (key, cursor, sp);
            }
        } while (!"0".equals (cursor));
        return map;
    }

    private Map<String, String> readAsMap (Jedis jedis, String key, String... fields) {
        Map<String, String> map = new HashMap<> ();
        if (fields.length == 0 || (fields.length == 1 && "*".equals (fields[0]))) {
            return query (key, "*");
        } else {
            List<String> data = jedis.hmget (key, fields);
            if (data != null && !data.isEmpty ()) {
                for (int i = 0; i < data.size (); i++) {
                    String value = data.get (i);
                    if (!StringUtil.isEmpty (value)) {
                        map.put (fields[i], value.trim ());
                    }
                }
            }
        }
        return map;
    }

    @Override
    public void dispose () {
        pool.destroy ();
    }
}