package org.dreamwork.integration.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ServiceProxyHandler implements InvocationHandler {
    private final Object target;
    private final ClassLoader loader;

    private final Logger logger = LoggerFactory.getLogger (ServiceProxyHandler.class);

    public ServiceProxyHandler (Object target, ClassLoader loader) {
        this.target = target;
        this.loader = loader;
    }

    @Override
    public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> type = target.getClass ();
        String name = method.getName ();
        Class<?>[] types = method.getParameterTypes ();

        Method m = type.getDeclaredMethod (name, types);
        Object o = m.invoke (target, args);
        if (o == null) {
            return null;
        }
        try {
            Class<?> c = o.getClass ();
            ClassLoader cl = c.getClassLoader ();
            if (cl == loader) {
                // 同一个 classloader，可安全直接返回
                return o;
            }
            if (cl != null) {
                if (cl.loadClass (c.getCanonicalName ()) == c) {
                    // 这个 c 是由 cl 及 this.loader 的公共父级加载的，可安全返回
                    return o;
                }

                // 不是公共父级加载，且 cl 和 loader 不是同一个
                Set<Class<?>> set = new HashSet<> ();
                findAllInterfaces (c, set);
                if (!set.isEmpty ()) {
                    return ServiceProxyFactory.createProxy (loader, o, set.toArray (new Class<?>[0]));
                }
            }
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }

        return o;
    }

    private void findAllInterfaces (Class<?> type, Set<Class<?>> set) {
        Class<?>[] interfaces = type.getInterfaces ();
        if (interfaces.length > 0) {
            set.addAll (Arrays.asList (interfaces));
        }
        if (type.getSuperclass () != Object.class) {
            findAllInterfaces (type.getSuperclass (), set);
        }
    }
}