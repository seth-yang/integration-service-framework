package org.dreamwork.integration.httpd.annotation;

import org.dreamwork.integration.httpd.support.AttributeScope;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AContextAttribute {
    String value() default "";
    String name() default "";
    AttributeScope scope() default AttributeScope.CONTEXT;
}