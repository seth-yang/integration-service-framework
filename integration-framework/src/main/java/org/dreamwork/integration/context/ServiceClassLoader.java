package org.dreamwork.integration.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by seth.yang on 2020/4/20
 */
public class ServiceClassLoader extends URLClassLoader {
    private final Logger logger = LoggerFactory.getLogger (ServiceClassLoader.class);
    private List<ShadowClassLoader> shadows =new ArrayList<>();

    public ServiceClassLoader (URL[] urls) {
        super (urls, Thread.currentThread ().getContextClassLoader ());
        for (URL url : urls) {
            ShadowClassLoader shadow = new ShadowClassLoader (url, this);
            shadows.add(shadow);
        }
    }

    @Override
    public Class<?> loadClass (String name) throws ClassNotFoundException {
        for (ShadowClassLoader loader : shadows) {
            try {
                return loader.loadClass (name);
            } catch (Exception ex) {
                // ignore, call next class loader
/*
               if(logger.isTraceEnabled()){
                   logger.trace(ex.getMessage(),ex);
               }
*/
            }
        }
        super.clearAssertionStatus ();
        throw new ClassNotFoundException (name);
    }

    public void reload () {
        /*if (shadow != null) {
            try {
                shadow.close ();
            } catch (IOException e) {
                e.printStackTrace ();
            }
        }
        shadow = new ShadowClassLoader (getURLs (), this);*/
    }

    private static final class ShadowClassLoader extends URLClassLoader {
        private final Logger logger = LoggerFactory.getLogger (ShadowClassLoader.class);
        public ShadowClassLoader (URL url, ClassLoader parent) {
            super (new URL[]{url}, parent);
        }

        @Override
        public Class<?> loadClass (String name) throws ClassNotFoundException {
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying to load class: {}", name);
            }
            return super.loadClass (name);
        }
    }
}
