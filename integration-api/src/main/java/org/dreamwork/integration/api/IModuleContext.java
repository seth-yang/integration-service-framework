package org.dreamwork.integration.api;

import org.dreamwork.config.IConfiguration;

import java.net.URL;

public interface IModuleContext {
    /**
     * 返回模块名称
     * @return 模块名称
     */
    String getName ();

    /**
     * 在框架中查找指定名称的服务.
     * <p>在模块运行时上下文容器中查找指定名称的服务实例。</p>
     * <p>通常，模块运行时上下文容器<code>IModuleContext</code>将<strong>委托</strong>实现框架进行服务注册和查询，这意味着
     * 您可以在一个模块中查找<strong>其他模块</strong>注册的服务</p>
     * @param name 服务名称
     * @param <T> 服务实例的类型
     * @return 服务实例。若未找到匹配的服务，将返回 <code>null</code>
     * @see #findService(Class)
     * @see #registerService(Class, Object)
     * @see #unRegisterService(Class, Object)
     */
    <T> T findService (String name);

    /**
     * 在框架中查找指定名称的服务.
     * <p>在模块运行时上下文容器中查找指定类型的服务实例。</p>
     * <p>通常，模块运行时上下文容器<code>IModuleContext</code>将<strong>委托</strong>实现框架进行服务注册和查询，这意味着
     * 您可以在一个模块中查找<strong>其他模块</strong>注册的服务</p>
     * @param type 服务类型, 必须是一个接口类型
     * @param <T> 服务实例的类型
     * @return 服务实例。若未找到匹配的服务，将返回 <code>null</code>
     * @see #findService(String)
     * @see #registerService(Class, Object)
     * @see #unRegisterService(Class, Object)
     */
    <T> T findService (Class<T> type);

    /**
     * 注册一个特定<strong>接口</strong>类型的服务.
     * <p>通常，模块运行时上下文容器<code>IModuleContext</code>将<strong>委托</strong>实现框架进行服务注册和查询</p>
     * @param type 接口类型
     * @param object 服务实例
     * @see #findService(String)
     * @see #findService(Class)
     * @see #unRegisterService(Class, Object)
     */
    void registerService (Class<?> type, Object object);

    /**
     * 注册一个特定<strong>接口</strong>类型的服务.
     * <p>通常，模块运行时上下文容器<code>IModuleContext</code>将<strong>委托</strong>实现框架进行服务注册和查询</p>
     * @param name 服务名称
     * @param object 服务实例
     * @see #findService(String)
     * @see #findService(Class)
     * @see #unRegisterService(Class, Object)
     */
    void registerService (String name, Object object);

    /**
     * 删除一个已经注册的特定<strong>接口</strong>类型的服务
     * @param type   接口类型
     * @param object 服务实例
     * @param <T>    接口类型
     * @see #findService(String)
     * @see #findService(Class)
     * @see #registerService(Class, Object)
     */
    <T> void unRegisterService (Class<T> type, T object);

    /**
     * 注册模块启动消息的监听器。
     * <p>框架允许一个模块监听<strong>其他模块</strong>启动和销毁的消息</p>
     * @param listener 模块监听器
     */
    void addModuleListener (IModuleListener listener);

    /**
     * 删除指定的模块启动消息的监听器。
     * <p>框架允许一个模块监听<strong>其他模块</strong>启动和销毁的消息</p>
     * @param listener 模块监听器
     */
    void removeModuleListener (IModuleListener listener);

    /**
     * 获取模块配置.
     * @return 模块配置
     */
    IConfiguration getConfiguration ();

    /**
     * 注册一个JMX MBean的<strong>委托</strong>方法.
     * <p>模块运行时上下文容器<code>IModuleContext</code>提供该方法来委托完成MBean的注册.</p>
     * @param name     MBean 名称
     * @param instance MBean 实例
     * @throws Exception 任何意外
     */
    void registerMBean (String name, Object instance) throws Exception;

    void unregisterMBean (String name) throws Exception;

    /**
     * 获取模块本地的classloader。
     * 每个非内置模块将有自己独立的classloader，和其他非内置模块互相隔离
     * @return 本地classloader
     */
    ClassLoader getContextClassLoader ();

    /**
     * 获取和模块相关联的资源。
     * ../work/${moduleName}/XXX
     *
     * @param name  资源名称
     * @return 相关资源
     */
    URL getResource (String name);

    ModuleInfo getInfo ();
}