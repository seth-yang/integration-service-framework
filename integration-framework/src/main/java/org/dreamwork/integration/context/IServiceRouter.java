package org.dreamwork.integration.context;

import org.dreamwork.integration.api.IModuleContext;

public interface IServiceRouter {
    <T> T findService (String name);
    <T> T findService (Class<T> type);
    void registerService (IModuleContext context, Object bean);
    void registerService (IModuleContext context, Class<?> type, Object object);
    void registerService (IModuleContext context, String name, Object bean);
    void unregisterService (Class<?> type, Object object);
    void clean (IModuleContext context);
}