package org.dreamwork.integration.api.services;

import org.dreamwork.db.IDatabase;

/**
 * Created by seth.yang on 2020/4/21
 */
public interface ISystemService {
    /**
     * 获取系统配置使用的统一 SQLite 数据库
     * @return sqlite 数据库连接对象
     */
    IDatabase getSystemDatabase ();
}
