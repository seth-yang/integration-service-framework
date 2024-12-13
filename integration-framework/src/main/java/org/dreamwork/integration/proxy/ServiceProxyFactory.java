package org.dreamwork.integration.proxy;

import java.lang.reflect.Proxy;

public class ServiceProxyFactory {
    @SuppressWarnings ("unchecked")
    public static<T> T createProxy (ClassLoader loader, Object target, Class<?>... interfaces) {
        if (target == null) return null;

        Class<?>[] copies = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i ++) {
            try {
                copies[i] = loader.loadClass (interfaces[i].getCanonicalName ());
            } catch (ClassNotFoundException e) {
                e.printStackTrace ();
            }
        }

        ServiceProxyHandler handler = new ServiceProxyHandler (target, loader);
        return  (T) Proxy.newProxyInstance (/*target.getClass ().getClassLoader ()*/loader, copies, handler);
    }
}
