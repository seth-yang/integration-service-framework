package org.dreamwork.integration.httpd.annotation;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ARequestAttribute {
    String value () default "";
    String name () default "";
}