package org.dreamwork.integration.api.services;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 基础的数据库连接服务
 */
public interface IDatabaseService {
    /**
     * 获取一个标准的 JDBC 连接
     * <p>已废弃，由 {@link #getConnection(String)} 替代</p>
     * @return JDBC 连接
     * @throws SQLException if any
     *
     * @see #getConnection(String)
     */
    @Deprecated
    Connection getConnection () throws SQLException;

    /**
     * 获取一个数据源
     * <p>已废弃，由 {@link #getDataSource(String)} 替代</p>
     * @return 数据源
     * @see #getDataSource(String)
     */
    @Deprecated
    DataSource getDataSource ();

    /**
     * 获取一个标准的 JDBC 连接
     * @param name 配置名称
     * @return JDBC 连接
     * @throws SQLException if any
     */
    Connection getConnection (String name) throws SQLException;

    /**
     * 获取一个数据源
     * @param name 配置名称
     * @return 数据源
     */
    DataSource getDataSource (String name);

    /**
     * 注册数据连接池
     *
     * 使用 apache dbcp 2 数据库连接池实现
     *
     * @param name       连接池名称
     * @param properties 连接池配置属性
     * @throws Exception any exception
     */
    void register (String name, Properties properties) throws Exception;

    /**
     * 删除数据库连接池配置
     * @param name 数据库连接池名称
     */
    void unregister (String name);
}