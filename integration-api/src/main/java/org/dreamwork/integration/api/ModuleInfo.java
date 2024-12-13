package org.dreamwork.integration.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by seth.yang on 2020/4/21
 */
public class ModuleInfo implements Serializable {
    public String name;
    public String impl;
    public String extra;
    public String context;
    public String version = "1.1.0";
    public String memo;
    public String[] apiPackages;
    public boolean internal;
    public boolean api;
    public boolean running;
    public String basedir;
    public boolean requireHttpd;

    public Map<String, ModuleInfo> dependencies = new HashMap<> ();

    public ModuleInfo (String name, String impl, boolean internal) {
        this.name = name;
        this.impl = impl;
        this.internal = internal;
    }

    public void addDependency (ModuleInfo info) {
        dependencies.put (info.name, info);
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass () != o.getClass ()) return false;
        ModuleInfo that = (ModuleInfo) o;
        return name != null && name.equals (that.name);
    }

    @Override
    public int hashCode () {
        return name == null ? 0 : name.hashCode ();
    }

    @Override
    public String toString () {
        StringBuilder builder = new StringBuilder (name);
        if (!dependencies.isEmpty ()) {
            builder.append ("->").append (dependencies.values ());
        }
        return builder.toString ();
    }
}