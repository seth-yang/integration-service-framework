package org.dreamwork.integration.api.services;

import org.dreamwork.util.IDisposable;

import java.util.Properties;

public interface IRedisManager extends IDisposable {
    /**
     * 根据给定的表达式，查找并指定的redis服务
     * <p>其中，表达式可以有两种形式：
     * <ol>
     * <li>配置名.数据库名</li>
     * <li>配置名[数据库索引]</li>
     * </ol>
     * @param name 表达式
     * @return 匹配的redis服务实例。如果没有匹配的，返回 null
     */
    IRedisService getByName (String name);

    /**
     * 根据指定的配置名称和数据库名称来获取 redis 服务
     *
     * @param name         配置名称
     * @param databaseName 数据库映射名称
     * @return 匹配的 redis 服务实例
     * @since 1.1.1
     * @see #get(String, int)
     */
    IRedisService get (String name, String databaseName);

    /**
     * 根据指定的配置名称和数据库名称来获取 redis 服务.
     * 如果该数据库索引值合法 <strong>0 &lt;= database &lt; 16</strong>时，
     * 即便之前未配置过，关联该数据库的 redis 服务也将被创建并返回。以后可以使用
     * 相同的配置名和数据库索引来查询; 或者为 {@link #getByName(String)} 方法
     * 提供 "name[database]" 格式的参数来查询
     *
     * @param name     配置名称
     * @param database 数据库索引
     * @return 匹配的 redis 服务实例
     * @since 1.1.1
     * @see #get(String, int)
     */
    IRedisService get (String name, int database);

    /**
     * 注册一个redis配置
     * @param name  配置名称
     * @param props 配置项
     */
    void register (String name, Properties props);

    /**
     * 向一个已经存在的配置项中添加一个数据库索引
     * @param name         配置名称
     * @param databaseName 数据库名称
     * @param database     数据库索引
     */
    void append (String name, String databaseName, int database);

    /**
     * 删除一个指定的 redis 配置
     * @param name 配置名称
     */
    void delete (String name);

    /**
     * 从一个 redis 配置中，删除一个数据库名称
     * @param name         配置名称
     * @param databaseName 数据库名称
     */
    void subtract (String name, String databaseName);
}