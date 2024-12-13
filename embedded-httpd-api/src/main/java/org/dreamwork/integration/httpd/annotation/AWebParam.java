package org.dreamwork.integration.httpd.annotation;

import org.dreamwork.integration.httpd.support.ParameterLocation;
import org.dreamwork.integration.httpd.support.ParameterType;

import java.lang.annotation.*;

import static org.dreamwork.integration.httpd.support.ParameterType.raw;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AWebParam {
    String value() default "";
    String name() default "";
    ParameterLocation location () default ParameterLocation.Auto;
    ParameterType type() default raw;
    String defaultValue() default "$$EMPTY$$";
}