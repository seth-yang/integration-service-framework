package org.dreamwork.integration.context;

import org.dreamwork.integration.api.IModuleClassLoader;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.util.Helper;
import org.dreamwork.integration.api.ModuleInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.jar.JarFile;

/**
 * Created by seth.yang on 2020/4/17
 */
public class ModuleClassLoader extends URLClassLoader implements IModuleClassLoader {
    private final Logger logger = LoggerFactory.getLogger (ModuleClassLoader.class);
    private final String name;
    private final String uuid = StringUtil.uuid ();

    private IModuleContext module;

    public ModuleClassLoader (String name, URL[] urls, ClassLoader parent) {
        super (urls, parent);
        this.name = name;
    }

    public ModuleInfo getModuleInfo () throws IOException {
        URL[] urls = this.getURLs ();
        if (urls != null) {
            for (URL url : urls) {
                String path = url.getFile ();
                if (path.endsWith (".jar")) {
                    ModuleInfo info = Helper.findModuleInfo (new File (path));
                    if (info != null) {
                        return info;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public URL getResource (String name) {
        URL[] urls = this.getURLs ();
        if (urls != null) {
            for (URL url : urls) {
                String path = url.getFile ();
                if (path.endsWith (".jar")) {
                    try (JarFile jar = new JarFile (new File (path))) {
                        if (jar.getEntry (name) != null) {
                            return new URL ("jar:file:" + path + "!/" + name);
                        }
                    } catch (Exception ex) {
                        //
                    }
                }
            }
        }


        return super.getResource (name);
    }

    @Override
    public Class<?> loadClass (String name) throws ClassNotFoundException {
        return super.loadClass (name);
    }

    @Override
    protected Class<?> findClass (String name) throws ClassNotFoundException {
        try {
            return super.findClass (name);
        } catch (ClassNotFoundException ex) {
            if (logger.isTraceEnabled ())
                logger.warn ("can't find class {} myself, call parent", name);
        }

        if (getParent () != null) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("parent = {}", getParent ());
            }
            return getParent ().loadClass (name);
        }
        throw new ClassNotFoundException (name);
    }

    /**
     * 销毁当前 classloader
     */
    public void destroy () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("[{}] destroying module class loader...", name);
        }
        module = null;
        try {
            super.close ();
        } catch (IOException ignored) {}
    }

    @Override
    public String toString () {
        return '[' + name + ':' + uuid + ':' + super.toString () + ']';
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass () != o.getClass ()) return false;
        ModuleClassLoader that = (ModuleClassLoader) o;
        return name != null && name.equals (that.name);
    }

    @Override
    public int hashCode () {
        return Objects.hash (name);
    }

    public void setModuleContext (IModuleContext module) {
        this.module = module;
    }

    @Override
    public IModuleContext getModuleContext () {
        return module;
    }
}