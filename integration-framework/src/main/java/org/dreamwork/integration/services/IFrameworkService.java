package org.dreamwork.integration.services;

import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.ModuleInfo;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Created by seth.yang on 2020/4/22
 */
public interface IFrameworkService {
    /**
     * 发布一个模块
     * @param src   一个包含模块相关配置的 jar 或者 zip
     * @return 如果发布成功，返回<code>模块目录</code>，否则返回<code>null</code>
     * @throws IOException io exception
     */
    String deploy (String src) throws IOException;

    /**
     * 取消一个模块的部署
     * @param moduleName 模块名称
     * @throws IOException 任何错误
     */
    void unDeploy(String moduleName) throws IOException;

    /**
     * 获取所有已经启动的模块名称
     * @return 模块名称列表
     */
    Set<String> getContextNames ();

    /**
     * 获取所有已注册的服务名
     * @return 服务名列表
     */
    Set<String> getServiceNames ();

    /**
     * 查找指定名称服务
     * @param name 服务名称
     * @return 如果指定名称的服务不存在，返回 null
     * @deprecated
     */
    @Deprecated
    Object findService (String name);

    /**
     * 启动一个指定名称的模块.
     * <p>这个模块必须已经发布，参见 {@link #deploy(String)}</p>
     * @param name 模块名称
     * @throws IntegrationException 启动过程中可能抛出的异常
     */
    void startModule (String name) throws IntegrationException;

    /**
     * 停止一个指定名称的模块.
     * <p>如果指定名称的模块不存在或未启动，没有影响</p>
     * @param name 模块名称
     * @throws IntegrationException 模块停止时可能抛出的异常
     */
    void stopModule (String name) throws IntegrationException;

    /**
     * 卸载指定的服务类加载器
     */
    void unloadServiceClassLoader ();

    /**
     * 加载指定的服务类加载器
     */
    void loadServiceClassLoader ();

    /**
     * 查询指定名称的模块信息
     * @param moduleName 模块名称
     * @return 如果模块存在，返回模块的详细信息，否则返回 null
     */
    Map<String, Object> getModuleInfo (String moduleName);

    ModuleInfo getModuleInfo2 (String name);

    /**
     * 获取数据库配置名称集合
     * @return 所有已注册在框架内的数据库配置名称
     */
    Set<String> getDatabaseConfigs ();
}
