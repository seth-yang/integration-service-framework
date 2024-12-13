package org.dreamwork.integration.internal;

import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

final class ServerClassLoader extends ClassLoader {
    private final Map<String, ClassLoader> contexts = new HashMap<> ();

    private final Logger logger = LoggerFactory.getLogger (ServerClassLoader.class);

    public ServerClassLoader () {
        super (Thread.currentThread ().getContextClassLoader ());
    }

    public void addContext (String path, ClassLoader loader) {
        synchronized (contexts) {
            contexts.put (path, loader);
        }
    }

    public void removeContext (String path) {
        synchronized (contexts) {
            contexts.remove (path);
        }
    }

    @Override
    protected Class<?> findClass (String name) throws ClassNotFoundException {
        if (!contexts.isEmpty ()) {
            ClassLoader loader = Thread.currentThread ().getContextClassLoader ();
            if (loader instanceof ParallelWebappClassLoader) {
                ParallelWebappClassLoader wcl = (ParallelWebappClassLoader) loader;
                String contextName = wcl.getContextName ();
                if (logger.isTraceEnabled ()) {
                    logger.trace ("current context: {}", contextName);
                }
                if (contexts.containsKey (contextName)) {
                    Class<?> c = contexts.get (contextName).loadClass (name);
                    if (c != null) {
                        return c;
                    }
                }
            }

            for (Map.Entry<String, ClassLoader> e : contexts.entrySet ()) {
                try {
                    Class<?> c = e.getValue ().loadClass (name);
                    if (c != null) {
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("class {} found by {}", c, e.getValue ());
                        }
                        return c;
                    }
                } catch (ClassNotFoundException ex) {
                    // ignore
                }
            }
        }
        return super.findClass (name);
    }
}
