package org.dreamwork.integration.api.annotation;

import java.lang.annotation.*;

@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AModule {
    /**
     * {@link #scanPackages() scanPackages} 的快捷方式
     * @return 需要扫描的包名
     */
    String[] value () default {};

    /**
     * 指定需要扫描的所有包名。 无论是否提供了该属性，扫描器都会扫描被标注对象的包
     * @return 需扫描的所有包名
     */
    String[] scanPackages () default {};

    /**
     * 是否递归扫描。默认 {@code false}
     * @return 是否递归扫描
     */
    boolean recursive () default false;
}