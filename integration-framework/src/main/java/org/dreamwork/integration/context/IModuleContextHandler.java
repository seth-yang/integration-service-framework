package org.dreamwork.integration.context;

import org.dreamwork.integration.api.IModuleListener;

public interface IModuleContextHandler {
    void addModuleListener (IModuleListener listener);
    void removeModuleListener (IModuleListener listener);
}
