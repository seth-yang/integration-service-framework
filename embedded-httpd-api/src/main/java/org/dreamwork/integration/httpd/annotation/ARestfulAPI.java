package org.dreamwork.integration.httpd.annotation;

import java.lang.annotation.*;

/**
 * 标注一个类成为集成框架的嵌入式 httpd 的一个 {@code Restful API} 的实现类.
 *
 * <p>在实现类中，可以直接使用键值 {@code IModule.CONTEXT_ATTRIBUTE_KEY} 在
 * {@link javax.servlet.ServletContext} 的属性集中查找 <strong>所属模块</strong> 的环境
 * <pre>&#64;ARestfulAPI ("/v1.0")
 * public class MyApi {
 *     &#64;ARestfulAPI ("/get-info")
 *     public String getInfo (ServletContext app) {
 *         IModuleContext context = (IModuleContext) app.getAttribute (IModule.CONTEXT_ATTRIBUTE_KEY);
 *         ...
 *     }
 * }
 * </pre>
 * 或者
 * <pre>&#64;ARestfulApi ("/v1.0")
 * public class MyAPI {
 *     &#64;ARestfulAPI ("/get-info")
 *     public String getInfo (@AContextAttribute (IModule.CONTEXT_ATTRIBUTE_KEY) IModuleContext context) {
 *         ...
 *     }
 * }</pre>
 *
 * IModule.CONTEXT_ATTRIBUTE_KEY
 */
@Target ({ElementType.TYPE, ElementType.METHOD})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ARestfulAPI {
    String[] value () default "";
    String[] urlPattern () default "";
    String method () default "get";

    String contentType () default "application/json;charset=utf-8";

    /**
     * 返回结果是否被包裹.
     * <p>若需要包裹， {@code dis-embedded-httpd} 框架会自动将结果包裹成固定格式的 json，其结构如下：
     * <pre>
     * {
     *     "code": int,
     *     "message": "string",
     *     "result": object
     * }</pre>
     * 其中 {@code result} 即为 handler 方法的返回结果，可能为 {@code null}
     * @return 若需要框架自动包裹成固定结构的json，则返回 true，否则返回 false
     * @since 1.1.0
     */
    boolean wrapped () default false;
}