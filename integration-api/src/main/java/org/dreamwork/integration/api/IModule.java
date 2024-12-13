package org.dreamwork.integration.api;

/**
 * 模块.
 *
 * <p>接口的实现类从逻辑上代表了一个独立<code>模块</code>。
 * </p>
 */
public interface IModule {
    /**
     * 模块启动的入口程序.
     * <p>这是模块启动时和运行时上下文容器交互的唯一接口</p>
     * <p><i>您不能阻塞这个接口。</i></p>
     * 如果这个方法运行时间超过 <code>integration.startup.timeout</code>参数配置的时间后，框架将抛出一个
     * {@link ModuleStartupTimeoutException}
     * @param context 模块运行的上下文容器
     * @throws IntegrationException if any
     */
    default void startup (IModuleContext context) throws IntegrationException {}

    /**
     * 当框架销毁模块时调用
     * @throws IntegrationException if any
     */
    default void destroy () throws IntegrationException {}

    /**
     * 如果该方法返回<i>非空</i>值时，框架将<code>模块</code>认为是一个<code>MBean</code>，会以返回值为名字注册
     * 到本地 JMX 服务中
     * @return JMX 名称
     */
    default String getMBeanName () {
        return null;
    }

    /** {@link IModuleContext} 在 ServletContext 中的索引值 */
    String CONTEXT_ATTRIBUTE_KEY = "org.dreamwork.integration.context.CONTEXT_ATTRIBUTE_KEY";
    /** 模块管理事件在本地广播中的 action */
    String ACTION_CONTEXT_EVENT  = "org.dreamwork.integration.context.ACTION_CONTEXT_EVENT";

    /** 模块启动代码 */
    int CODE_CONTEXT_STARTED     = 1;
    /** 模块停止代码 */
    int CODE_CONTEXT_STOPPED     = 2;
    /** 模块部署代码 */
    int CODE_CONTEXT_DEPLOYED    = 3;
    /** 模块删除代码 */
    int CODE_CONTEXT_REMOVED     = 4;
    /** 模块配置更新 */
    int CODE_CONF_CHANGED        = 5;
}