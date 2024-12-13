package org.dreamwork.integration.api;

public class ModuleStartupTimeoutException extends Exception {
    public ModuleStartupTimeoutException () {
    }

    public ModuleStartupTimeoutException (String message) {
        super (message);
    }

    public ModuleStartupTimeoutException (String message, Throwable cause) {
        super (message, cause);
    }

    public ModuleStartupTimeoutException (Throwable cause) {
        super (cause);
    }

    public ModuleStartupTimeoutException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super (message, cause, enableSuppression, writableStackTrace);
    }
}
