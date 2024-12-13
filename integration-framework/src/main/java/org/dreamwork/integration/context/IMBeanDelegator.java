package org.dreamwork.integration.context;

public interface IMBeanDelegator {
    void registerMBean (String name, Object instance) throws Exception;
    void unregisterMBean (String name) throws Exception;
}
