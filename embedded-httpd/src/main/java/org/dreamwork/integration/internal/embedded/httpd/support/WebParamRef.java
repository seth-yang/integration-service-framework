package org.dreamwork.integration.internal.embedded.httpd.support;

import org.dreamwork.integration.httpd.support.ParameterLocation;
import org.dreamwork.integration.httpd.support.ParameterType;

public class WebParamRef {
    public String name, defaultValue, contentType;
    public ParameterLocation location = ParameterLocation.Auto;
    public boolean internal;
    /**
     * @since 1.1.0
     */
    public boolean nullable;
    public ParameterType type;
}