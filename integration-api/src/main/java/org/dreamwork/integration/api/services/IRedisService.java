package org.dreamwork.integration.api.services;

import org.dreamwork.util.IDisposable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IRedisService extends IDisposable {
    /////////////////////////////////////// 通用操作 //////////////////////////////
    /**
     * 从 redis 中获取指定 key 的值
     * @param key 键
     * @return key 对应的值
     */
    String get (String key);

    /**
     * 向 redis 中写入一个键值对
     * @param key   键
     * @param value 值
     */
    void set (String key, String value);

    /**
     * 查询一个主键是否存在
     * @param key 主键值
     * @return 若主键 key 在 redis 缓存中存在返回 true, 否则返回 false
     */
    boolean present (String key);

    /**
     * 查询指定的模式的key是否存在
     * @param pattern key的模式
     * @return 若存在返回 true，否则返回 false
     */
    boolean matches (String pattern);

    /**
     * 查询指定模式的key的所有集合
     * @param pattern key的模式
     * @return 匹配模式 {@code pattern} 的所有的key
     */
    List<String> queryKeys (String pattern);

    /**
     * 查询指定模式的key的结果集
     * @param pattern key 的模式
     * @return 所有匹配模式 {@code pattern} 的所有结果集
     */
    Map<String, String> query (String pattern);

    /**
     * 删除指定的缓存
     * @param keys 主键
     */
    void delete (String... keys);

    //////////////////////////////////// 生命周期操作 //////////////////////////////
    /**
     * 设置一个key的值的存活相对时间，单位为{@code 秒}
     * @param key             键值
     * @param offsetInSeconds 存活的相对时间，秒
     * @return 若设置成功返回 true，否则返回 false
     */
    boolean setExpiredIn (String key, int offsetInSeconds);

    /**
     * 设置一个 key 的存活绝对时间戳，单位 {@code 毫秒}
     * @param key       键
     * @param timestamp 绝对时间， java timestamp
     * @return 若设置成功返回 true，否则返回 false
     */
    boolean setExpiredAt (String key, long timestamp);

    /**
     * 删除一个键的过期设置
     * @param key 键值
     * @return 删除成功返回 true，否则返回 false
     */
    boolean removeExpiration (String key);

    /**
     * 获取一个键的剩余存活时间
     * @param key 键
     * @return 若指定的 {@code key} 没有设置过超时时间，返回返回 -1；若键不存在或已经过期，返回 -2，否则返回键的剩余存活时间 ({@code 毫秒}
     */
    long getLifetime (String key);

    /**
     * 设置一个具有存活时间({@code 毫秒})的键值对
     * @param key     键
     * @param value   值
     * @param timeout 超时相对时间，毫秒
     */
    void setValueWithTimeout (String key, String value, long timeout);

    /////////////////////////////////////// 字典操作 //////////////////////////////

    /**
     * 写入一个 map 到 redis 缓存
     * @param key 主键名
     * @param map 数据
     */
    void writeMap (String key, Map<String, String> map);

    /**
     * 写入一组 map 到 redis 缓存
     * @param keys 主键列表
     * @param maps 值列表
     */
    void writeMaps (String[] keys, Map<String, String>[] maps);

    /**
     * 从 redis 缓存读取指定 key 的 map 中的指定字段，返回值的顺序和字段名称的顺序一致
     * @param key    主键值
     * @param fields map的字段名列表
     * @return 返回值, 顺序和字段名称的顺序一致
     */
    String[] readMapValues (String key, String... fields);

    /**
     * 从 redis 缓存读取指定 key 的 map 中的指定字段，返回的 map 中包换所有有值的指定字段
     * @param key    主键值
     * @param fields map的字段列表
     * @return 如果某个字段在 redis 缓存中无值，则不会被包含在结果中
     */
    Map<String, String> readAsMap (String key, String... fields);

    /**
     * 从指定 key 的 map 中读取一个字段值
     * @param key   主键
     * @param field map 的字段名
     * @return 字段值
     */
    String readValue (String key, String field);

    /**
     * 写入指定 key 的 map 中的一个字段的值
     * @param key   键值
     * @param field map 中的字段名
     * @param value 字段值
     */
    void writeValue (String key, String field, String value);

    /**
     * 删除指定key的字典中的指定字段
     * @param key    字典的key
     * @param fields 字典的字段
     */
    void deleteField (String key, String... fields);

    /**
     * 查询指定字典内是否有制定的字段
     * @param key   字典的索引
     * @param field 字典内的字段名
     * @return 若字典不存在，或字典内字段不存在，返回 false，否则返回true
     */
    boolean present (String key, String field);

    /**
     * 查询指定key的字典内是否存在模式为 {@code pattern} 的字段
     * @param key     字典的 key
     * @param pattern 待查询的字段名的模式
     * @return 若查询到返回 true，否则返回 false
     */
    boolean matches (String key, String pattern);

    /**
     * 查询指定{@code key}的字典内部符合模式 {@code pattern} 的结果集
     * @param key     字典的 key
     * @param pattern 字典内字段名模式
     * @return 所有匹配模式 {@code  pattern} 的结果集
     */
    Map<String, String> query (String key, String pattern);

    /**
     * 查找所有满足模式 {@code pattern} 的key的字典
     * @param pattern 字典的key的模式
     * @return 所有匹配模式 {@code pattern} 的字典的结果集
     */
    Map<String, Map<String, String>> queryAsMaps (String pattern);

    ////////////////////////////////// 列表操作 ///////////////////////////////

    /**
     * 在{@code key}指定的列表<strong>最右侧</strong>压入值
     * @param key    列表键名
     * @param values 要压入的值
     */
    void push (String key, String... values);

    /**
     * 弹出{@code key}指定的列表的<strong>最左边</strong>的元素
     * @param key 列表的键名
     * @return 最左侧的元素
     */
    String take (String key);

    /**
     * 弹出{@code key}指定的列表<strong>最右侧</strong>的元素
     * @param key 列表的键名
     * @return 最右侧的元素
     */
    String pop (String key);

    /**
     * 获取由{@code key}指定的列表的长度
     * @param key 列表的键名
     * @return 列表的长度
     */
    long getListLength (String key);

    /**
     * 更新由{@code key}指定的列表中，由{@code index}位置上的元素值
     * @param key   列表的键名
     * @param index 列表中的位置索引
     * @param value 新的值
     */
    void setListItem (String key, int index, String value);

    /**
     * 获取由{@code key}指定的列表中，从{@code start}到{@code end}的子列表
     * @param key   列表的键名
     * @param start 开始下标
     * @param end   结束下标
     * @return 子列表
     */
    List<String> sublist (String key, int start, int end);

    /**
     * 获取由{@code key}指定的列表中由{@code index}索引的元素
     * @param key   列表的键值
     * @param index 列表内的索引
     * @return 指定的值
     */
    String getItem (String key, int index);

    //////////////////////////// 集合操作 //////////////////////////

    /**
     * 向 {@code key} 指定的集合内添加 {@code values} 元素
     * @param key    集合的键名
     * @param values 要添加到集合中的值
     */
    void addSetItem (String key, String... values);

    /**
     * 从 {@code key} 指定的集合内删除 {@code values} 元素
     * @param key    集合的键名
     * @param values 要删除的值
     */
    void removeSetItem (String key, String... values);

    /**
     * 判断 {@code value} 是否在 {@code key} 指定的集合内
     * @param key   集合的键名
     * @param value 值
     * @return 若 {@code value} 存在于 {@code key} 指定的集合内返回 true, 否则返回 false
     */
    boolean isInSet (String key, String value);

    /**
     * 获取由 {@code key} 指定的集合的大小
     * @param key 集合的键名
     * @return 集合的大小
     */
    long getSetSize (String key);

    /**
     * 获取由 {@code key} 指定集合内的所有成员
     * @param key 集合的键名
     * @return 集合的所有成员
     */
    Set<String> getMembers (String key);

    //////////////////////////// 有序集合操作 //////////////////////////
    double increase (String key, String member);
    void resetCounter (String key, String member);
}