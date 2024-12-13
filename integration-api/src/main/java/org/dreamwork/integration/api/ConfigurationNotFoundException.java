package org.dreamwork.integration.api;

/**
 * 当需要注入的配置项不存在，且资源被标注为 {@code required} 时抛出这个异常
 */
public class ConfigurationNotFoundException extends RuntimeException {
    public ConfigurationNotFoundException () {
        super ();
    }

    public ConfigurationNotFoundException (String message) {
        super (message);
    }

    public ConfigurationNotFoundException (String message, Throwable cause) {
        super (message, cause);
    }

    public ConfigurationNotFoundException (Throwable cause) {
        super (cause);
    }

    protected ConfigurationNotFoundException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super (message, cause, enableSuppression, writableStackTrace);
    }
}