package org.dreamwork.integration.context;

import org.dreamwork.util.ResourceUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基本的类扫描器
 */
public abstract class ClassScanner {
    private final Logger logger = LoggerFactory.getLogger (ClassScanner.class);

    protected final ClassLoader classloader;

    public ClassScanner (ClassLoader classloader) {
        this.classloader = classloader;
    }

    /**
     * 扫描给定名称的所有包下的类。
     *
     * @param packageNames 给定的所有包名
     * @throws Exception 任何异常
     */
    public void scan (String... packageNames) throws Exception {
        Set<Wrapper> wrappers = new HashSet<> ();
        try {
            Set<Class<?>> matchedClasses = new HashSet<> ();
            for (String packageName : packageNames) {
                List<Class<?>> list = ResourceUtil.getClasses (packageName, classloader);
                if (!list.isEmpty ()) {
                    for (Class<?> type : list) {
                        if (accept (type)) {
                            matchedClasses.add (type);
                        }
                    }
                }
            }
            if (!matchedClasses.isEmpty ()) {
                for (Class<?> type : matchedClasses) {
                    try {
                        Resource annotation = type.getAnnotation (Resource.class);
                        String name = annotation.name ();
                        if (StringUtil.isEmpty (name)) {
                            name = type.getSimpleName ();
                        }
                        onFound (name, type, wrappers);
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                        throw ex;
                    }
                }
            }
        } finally {
            onCompleted (wrappers);
        }
    }

    /**
     * 当扫描器扫描到一个类时调用这个方法来验证是否是所需的，若是，则触发 {@link #onFound(String, Class, Set)} 事件
     * @param type java 类
     * @return 如果是所需的返回 {@code true}，否在返回 {@code false}
     */
    protected abstract boolean accept (Class<?> type);

    /**
     * 当扫描器找到所需的类时触发该事件。
     *
     * <p>该方法的实现应该处理这个事件。
     * 若当即无法处理的逻辑，比如 <i><strong>{@code 依赖注入}</strong></i> 等，需要所有扫描结果出来后再处理的，
     * 可以为处理结果创建一个包裹类 {@link Wrapper} 放在 {@code wrappers} 出参里，
     * 等到 {@link #onCompleted(Set)} 事件来统一处理
     * </p>
     * @param name     类的简短名称
     * @param type     java 类型
     * @param wrappers 包裹类
     * @throws Exception 任何异常
     * @see #onCompleted(Set)
     */
    protected abstract void onFound (String name, Class<?> type, Set<Wrapper> wrappers) throws Exception;

    /**
     * 当所有包都扫描完成，扫描器将触发这个事件.
     *
     * <p>如果在 {@link #onFound(String, Class, Set)} 事件中有未处理完的逻辑可以在这个事件中完成。</p>
     *
     * @param wrappers 包裹类集合
     * @throws Exception 任何异常
     * @see #onFound(String, Class, Set)
     */
    protected abstract void onCompleted (Set<Wrapper> wrappers) throws Exception;

    /**
     * 包裹类，包含一些简单信息
     */
    public static final class Wrapper {
        public Class<?> type;
        public Object bean;

        public Set<Field> injectFields = new HashSet<> ();
        public Set<Field> configuredFields = new HashSet<> ();
        public Set<MethodWrapper> injectMethods = new HashSet<> ();
        public Set<MethodWrapper> exposeMethods = new HashSet<> ();
        public Method postConstruct, preDestroy;
    }

    public static final class MethodWrapper {
        public String name;
        public Method method;
    }
}