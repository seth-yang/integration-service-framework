package org.dreamwork.integration.context;

import org.dreamwork.integration.api.IModuleClassLoader;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.annotation.AConfigured;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.management.InstanceNotFoundException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.dreamwork.integration.util.Helper.configureFields;

public class SimpleServiceRouter implements IServiceRouter {
    /**
     * 排除的包名。
     *
     * <p>某些 jdk 自带的接口不适合作为类型索引，应该排除它们</p>
     */
    private static final String[] EXCLUDE_PREFIXES = {
            "java.util.", "java.io."
    };

    private final Logger logger = LoggerFactory.getLogger (SimpleServiceRouter.class);

    protected final Map<String, Object> mappedByName = new HashMap<> ();
    protected final Map<Class<?>, Object> mappedByType = Collections.synchronizedMap (new HashMap<> ());
    protected final Map<String, ContextCache> caches = new ConcurrentHashMap<> ();

    private final AtomicBoolean resolved = new AtomicBoolean (false);

    @Override
    @SuppressWarnings ("unchecked")
    synchronized public <T> T findService (String name) {
        return (T) mappedByName.get (name);
    }

    @SuppressWarnings ("unchecked")
    @Override
    synchronized public <T> T findService (Class<T> type) {
        Object o = mappedByType.get (type);
        if (o instanceof InnerList) {
            InnerList il = (InnerList) o;
            if (il.size () == 1) {
                return (T) il.get (0);
            }
            throw new RuntimeException ("there's more than one instance referenced by type " + type + ", use findService(String) instead.");
        }

        return (T) o;
    }

    @Override
    public void registerService (IModuleContext context, Object bean) {
        if (bean == null) {
            throw new NullPointerException ("cannot register a null object");
        }
        Class<?> type = bean.getClass ();
        registerService (context, getSimpleName (type), bean);
    }

    @Override
    synchronized public void registerService (IModuleContext context, Class<?> type, Object object) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("registering service::{} as {}", type, object);
        }
        registerService (context, getSimpleName (type), object);
    }

    @Override
    public void registerService (IModuleContext context, String name, Object bean) {
        if (bean == null) {
            throw new RuntimeException ("cannot register a null object");
        }

        synchronized (this) {
            if (mappedByName.containsKey (name)) {
                throw new RuntimeException ("name [" + name + "] already exists.");
            }
            // 添加到命名映射中
            mappedByName.put (name, bean);
            ContextCache cache = caches.computeIfAbsent (context.getName (), key -> new ContextCache ());
            cache.names.add (name);

            // 为了能够在客户代码中通过实例的任意级别的类 (java.lang.Object除外) 来索引实例
            // 这里必须展开这个实例的继承树
            Set<Class<?>> types = new HashSet<> ();
            // 获取继承树上的各个类型
            findAllType (bean.getClass (), types);

            if (logger.isTraceEnabled ()) {
                logger.trace ("found all types: {}", types);
            }

            if (!types.isEmpty ()) {
                // 预处理方法
                Method postConstruct = null;
                Set<Field> set = new HashSet<> ();
                for (Class<?> type : types) {
                    if (!mappedByType.containsKey (type)) {
                        // 若这个类型的实例未被映射过，直接映射
                        mappedByType.put (type, bean);
                        cache.add (type, bean);
                    } else {
                        // 曾经映射过
                        Object o = mappedByType.get (type);
                        if (o instanceof InnerList) {
                            // 如果类型索引的是一个列表，往列表中添加实例
                            InnerList il = (InnerList) o;
                            if (!il.contains (bean)) {
                                il.add (bean);
                                cache.add (type, bean);
                            }
                        } else {
                            // 将原先映射的实例转成列表
                            InnerList il = new InnerList ();
                            il.add (o);
                            il.add (bean);
                            mappedByType.put (type, il);
                            cache.add (type, bean);
                        }
                    }

                    // 曾经已经解决了依赖注入，当对象被注入后，需要再次解决注入依赖
                    if (resolved.get ()) {
                        if (!type.isInterface ()) {
                            try {
                                Method method = resolve (context, bean, type);
                                if (method != null) {
                                    if (postConstruct != null) {
                                        throw new RuntimeException ("Only ONE method can be annotated as PostConstruct.");
                                    }
                                    postConstruct = method;
                                }
                            } catch (Exception ex) {
                                logger.warn (ex.getMessage (), ex);
                                if (ex instanceof RuntimeException) {
                                    throw (RuntimeException) ex;
                                } else {
                                    throw new RuntimeException (ex);
                                }
                            }
                        }
                    }

                    Field[] fields = type.getDeclaredFields ();
                    for (Field field : fields) {
                        if (field.isAnnotationPresent (AConfigured.class)) {
                            // 标注为配置注入的字段
                            set.add (field);
                        }
                    }
                }

                // 执行预处理方法
                if (postConstruct != null) {
                    try {
                        postConstruct.invoke (bean);
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                        throw new RuntimeException (ex);
                    }
                }

                if (!set.isEmpty ()) {
                    // 配置注入
                    IConfiguration conf = findService (IConfiguration.class);
                    if (conf != null) {
                        try {
                            configureFields (conf, bean, set);
                        } catch (IllegalAccessException ex) {
                            logger.warn (ex.getMessage (), ex);
                            throw new RuntimeException (ex);
                        }
                    }
                }
            }
        }
    }

    private Method resolve (IModuleContext context, Object bean, Class<?> type) throws InvocationTargetException, IllegalAccessException, InstanceNotFoundException {
        Method postConstruct = null;
        // 注入需要注入的字段
        Field[] fields = type.getDeclaredFields ();
        for (Field field : fields) {
            if (field.isAnnotationPresent (Resource.class)) {
                injectField (context, bean, field);
            }
        }
        // 注入需要处理的方法
        Method[] methods = type.getDeclaredMethods ();
        for (Method method : methods) {
            if (method.isAnnotationPresent (Resource.class)) {
                int code = map (method);
                switch (code) {
                    case 1: // 标注为 Resource 的方法
                        processResourceMethod (context, bean, method);
                        break;
                    case 2: // 标注为 PostConstruct 的方法
                        postConstruct = method;
                        break;
                }
            }
        }

        return postConstruct;
    }

    @Override
    synchronized public void unregisterService (Class<?> type, Object object) {
        Object o = mappedByType.get (type);
        if (o != null) {
            if (o instanceof InnerList) {
                InnerList il = (InnerList) o;
                il.remove (object);
                if (il.isEmpty ()) {
                    mappedByType.remove (type);
                }
            } else {
                mappedByType.remove (type);
            }
        }
    }

    @Override
    public void clean (IModuleContext context) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to clean services what registered by context: {}", context.getName ());
        }
        ContextCache cache = caches.remove (context.getName ());
        if (cache != null) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("hit a target!!");
            }
            if (!cache.names.isEmpty ()) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("cleaning the service referenced by names: {}", cache.names);
                }
                cache.names.forEach (mappedByName::remove);
            }
            if (!cache.objects.isEmpty ()) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("cleaning the services referenced by type ...");
                }
                cache.objects.forEach ((type, list) -> {
                    if (list != null && !list.isEmpty ()) {
                        list.forEach (o -> unregisterService (type, o));
                    }
                });
            }
        }
    }

    synchronized Set<String> getServiceNames () {
        return mappedByName.keySet ();
    }

    /**
     * 展开类的继承树，并将每个层级的类放在集合中
     * @param baseType 基本类型
     * @param types    出参。每个层级的类型都会被放在这个集合中
     */
    private void findAllType (Class<?> baseType, Set<Class<?>> types) {
        Class<?> type = baseType;
        while (type != null && type != Object.class) {
            types.add (type);

            Class<?>[] temp = type.getInterfaces ();
            for (Class<?> t : temp) {
                String name = t.getCanonicalName ();
                if (exclude (name)) {
                    continue;
                }
                findAllType (t, types);
            }

            type = type.getSuperclass ();
        }
    }

    /**
     * 映射方法类型。
     * <ul>
     * <li>只有public方法才会被映射</li>
     * <li>标注为 {@link Resource} 的方法映射为 1</li>
     * <li>标注为 {@link PostConstruct} 的方法映射为 2</li>
     * <li>标注为 {@link PreDestroy} 的方法映射为 3</li>
     * <li>其他映射为 -1</li>
     * </ul>
     * @param method 需要映射的方法
     * @return 映射后的代码
     */
    private int map (Method method) {
        int code = -1;
        if (method.isAnnotationPresent (Resource.class)) {
            code = 1;
        } else if (method.isAnnotationPresent (PostConstruct.class)) {
            code = 2;
        } else if (method.isAnnotationPresent (PreDestroy.class)) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("found a pre-destroy method of {}: {}", method.getDeclaringClass (), method);
            }
            code = 3;
        }

        int modifier = method.getModifiers ();
        if ((modifier & Modifier.PUBLIC) != 0) {
            return code;
        }
        return -1;
    }

    /**
     * 将资源注入字段
     * @param context 模块上下文
     * @param bean  对象实例
     * @param field 被标注为自动注入资源的字段
     * @throws InstanceNotFoundException 被注入的资源不存在时抛出
     * @throws IllegalAccessException 无法访问目标字段时抛出
     */
    public void injectField (IModuleContext context, Object bean, Field field) throws InstanceNotFoundException, IllegalAccessException {
        Class<?> ft = field.getType ();
        Object value;
        if (IModuleContext.class.isAssignableFrom (ft)) {
            value = context;
        } else {
            Resource res = field.getAnnotation (Resource.class);
            if (!StringUtil.isEmpty (res.name ())) {
                value = findService (res.name ());
            } else {
                value = findService (ft);
            }
        }

        if (value == null) {
            throw new InstanceNotFoundException ("field " + field + " cannot be injected. The annotated object was not registered.");
        }

        if (!field.isAccessible ()) {
            field.setAccessible (true);
        }

        field.set (bean, value);
    }

    /**
     * 将资源通过 setter 方法注入
     * @param context 模块上下文
     * @param name   资源名称. 若该参数为 "" 或 {@code null} 时将使用 setter 的参数类型为索引来查找资源
     * @param bean   对象实例
     * @param method 自动注入的 setter
     * @throws InstanceNotFoundException 被注入的资源不存在时抛出
     * @throws InvocationTargetException 无法调用 setter 时抛出
     * @throws IllegalAccessException 无法访问 setter 时抛出
     */
    public void injectMethod (IModuleContext context, String name, Object bean, Method method) throws InstanceNotFoundException, InvocationTargetException, IllegalAccessException {
        Object value;
        if (!StringUtil.isEmpty (name)) {
            value = findService (name);
        } else {
            Class<?> type = method.getParameterTypes ()[0];
            if (IModuleContext.class.isAssignableFrom (type)) {
                value = context;
            } else {
                value = findService (type);
            }
        }

        if (value == null) {
            throw new InstanceNotFoundException ("method " + method + " cannot be injected. The annotated object was not registered.");
        }
        method.invoke (bean, value);
    }

    /**
     * 处理被标注为资源的方法。
     *
     * <p>{@code getter} 和 {@code setter} 都可能被标注为资源</p>
     * <ul>
     * <li>当一个 setter 被标注为资源时，意味着需要将资源注入</li>
     * <li>当一个 getter 被标注为资源时，意味着返回值需要注册到托管容器内</li></ul>
     * @param context 模块上下文
     * @param bean   对象实例
     * @param method 标注为资源的 getter 或 setter
     * @throws InvocationTargetException 调用错误
     * @throws IllegalAccessException 没有权限执行反射函数
     */
    public void processResourceMethod (IModuleContext context, Object bean, Method method) throws InvocationTargetException, IllegalAccessException {
        String name = method.getName ();
        Resource res = method.getAnnotation (Resource.class);

        if (name.startsWith ("get")) {  // getter 方法，意味着应该将返回值注入到容器内
            Class<?> type = method.getReturnType ();
            if (type == void.class || type == Void.class) {
                throw new RuntimeException ("a method annotated as Resource getter MUST return something");
            }
            if (method.getParameterCount () != 0) {
                throw new RuntimeException ("a method annotated as Resource getter CANNOT have any parameters");
            }
            String beanName = res.name ();
            if (StringUtil.isEmpty (beanName)) {
                beanName = type.getSimpleName ();
                beanName = Character.toLowerCase (beanName.charAt (0)) + beanName.substring (1);
            }
            Object value = method.invoke (bean);
            if (value == null) {
                throw new RuntimeException ("The method " + method + " returns null");
            }
            registerService (context, beanName, value);
        } else if (name.startsWith ("set")) { // setter 方法，意味着应该注入参数类型的对象
            if (method.getParameterCount () != 1) {
                throw new RuntimeException ("a method annotated as Resource setter MUST HAVE ONLY ONE parameter");
            }
            Class<?> type = method.getParameterTypes ()[0];
            Object value = null;
            if (type.isAssignableFrom (getClass ())) {
                value = this;
            } else if (IModuleContext.class.isAssignableFrom (type)) {
                ClassLoader loader = method.getDeclaringClass ().getClassLoader ();
                if (loader instanceof IModuleClassLoader) {
                    value = ((IModuleClassLoader) loader).getModuleContext ();
                }
            } else {
                if (!StringUtil.isEmpty (res.name ())) {
                    value = findService (res.name ());
                } else {
                    value = findService (type);
                }
            }
            if (value == null) {
                throw new RuntimeException ("cannot find bean: " + type);
            }
            method.invoke (bean, value);
        }
    }

    public void setResolved (boolean resolved) {
        this.resolved.set (resolved);
    }

    /**
     * 是否是排除 {@link #EXCLUDE_PREFIXES} 列表中的类
     * @param name 类的全限定名称
     * @return 若是返回 {@code true}，否则 {@code false}
     */
    private boolean exclude (String name) {
        if (name.startsWith ("java.util.concurrent.")) {
            return false;
        }
        for (String prefix : EXCLUDE_PREFIXES) {
            if (name.startsWith (prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将java类型名称转换成 java 属性风格的字符串
     * @param type java 类型
     * @return java 属性风格的字符串
     */
    private static String getSimpleName (Class<?> type) {
        String name = type.getSimpleName ();
        if (name.length () == 1) {
            return name.toLowerCase ();
        }
        return Character.toLowerCase (name.charAt (0)) + name.substring (1);
    }

    public static final class InnerList extends LinkedList<Object> {}

    public static class ContextCache implements Serializable {
        Set<String> names = new HashSet<> ();
        Map<Class<?>, List<Object>> objects = new HashMap<> ();

        public void add (Class<?> type, Object o) {
            List<Object> list = objects.computeIfAbsent (type, key -> new ArrayList<> ());
            list.add (o);
        }
    }
}