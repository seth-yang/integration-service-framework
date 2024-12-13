package org.dreamwork.integration.httpd.annotation;

import org.dreamwork.integration.httpd.support.ParameterType;

import java.lang.annotation.*;

import static org.dreamwork.integration.httpd.support.ParameterType.raw;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AFormItem {
    String value () default "";
    String name () default "";
    ParameterType type() default raw;
    String defaultValue() default "$$EMPTY$$";
}