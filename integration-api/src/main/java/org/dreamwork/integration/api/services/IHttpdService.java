package org.dreamwork.integration.api.services;

import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.ModuleInfo;

import java.nio.file.Path;

/**
 * Created by seth.yang on 2019/12/5
 */
public interface IHttpdService {
    void attach (IModuleContext context, Path workdir);

    void detach (ModuleInfo module);
}