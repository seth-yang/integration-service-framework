package org.dreamwork.integration.services.impl;

import org.dreamwork.integration.api.services.IDatabaseService;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class DataSourceService implements IDatabaseService {
    private DataSource datasource;
    private final Map<String, DataSource> pool = new HashMap<> ();

    @Deprecated
    public DataSourceService (Properties props) throws Exception {
        datasource = BasicDataSourceFactory.createDataSource (props);
    }

    public DataSourceService () {}

    @Override
    @Deprecated
    public Connection getConnection () {
        throw new RuntimeException ("Not Implemented");
    }

    @Override
    @Deprecated
    public DataSource getDataSource () {
        return datasource;
    }

    @Override
    public Connection getConnection (String name) throws SQLException {
        DataSource ds = getDataSource (name);
        return ds == null ? null : ds.getConnection ();
    }

    @Override
    public DataSource getDataSource (String name) {
        return pool.get (name);
    }

    @Override
    public synchronized void register (String name, Properties properties) throws Exception {
        DataSource ds = BasicDataSourceFactory.createDataSource (properties);
        pool.putIfAbsent (name, ds);
    }

    @Override
    public synchronized void unregister (String name) {
        BasicDataSource bds = (BasicDataSource) pool.get (name);
        if (bds != null) {
            try {
                bds.close ();
            } catch (SQLException e) {
                e.printStackTrace ();
            }
        }
        pool.remove (name);
    }

    public Set<String> getConfigNames () {
        return new HashSet<> (pool.keySet ());
    }
}