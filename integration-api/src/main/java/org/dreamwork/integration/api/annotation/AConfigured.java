package org.dreamwork.integration.api.annotation;

import org.dreamwork.integration.api.ConfigurationNotFoundException;

import java.lang.annotation.*;

/**
 * 表示被标注的对象可以由配置来获取值.
 *
 * <ul>
 * <li>当表达式为 ${a.b.c.d} 时，代表着从全局配置文件中获取 a.b.c.d 的值。当在配置文件中未匹配到键值时不会注入</li>
 * <li>当表达式为常量时，直接将常量赋值给被标注的对象</li>
 * <li>默认值 "" 表示直接使用 package.class.field 的形式作为键值在配置文件中进行匹配</li>
 * </ul>
 */
@Target ({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AConfigured {
    /**
     * 被注入的配置项键值. {@link #key()} 的快捷方式
     *
     * <p>默认值 "" 表示键值和字段值同名</p>
     * @return 被注入的配置项键值
     */
    String value () default "";

    /**
     * 被注入的配置项键值
     * <p>默认值 "" 表示键值和字段值同名</p>
     *
     * @return 被注入的配置项键值
     */
    String key () default "";

    /**
     * 该注入的配置项是否是必须的.
     * <p>若该属性为 <strong>{@code true}</strong> 时，<strong>且</strong> 在配置文件中
     * <strong>未找到</strong> 该键值时，扫描器将抛出 {@link ConfigurationNotFoundException} 异常
     * </p>
     *
     * @return 必须注入时 {@code true}，否在 {@code false}
     */
    boolean required () default false;
}