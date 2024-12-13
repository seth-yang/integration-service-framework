package org.dreamwork.integration.api;

public interface IModuleListener {
    /**
     * 当一个模块被框架<strong>启动后</strong>触发
     * @param context 模块运行时上下文容器
     */
    void onContextStartup (IModuleContext context);

    /**
     * 当一个模块被框架<strong>销毁前</strong>触发
     * @param context 模块运行时上下文容器
     */
    void onContextDestroy (IModuleContext context);
}