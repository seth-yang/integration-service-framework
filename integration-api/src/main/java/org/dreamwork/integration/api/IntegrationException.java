package org.dreamwork.integration.api;

public class IntegrationException extends Exception {
    public IntegrationException () {
        super ();
    }

    public IntegrationException (String message) {
        super (message);
    }

    public IntegrationException (String message, Throwable cause) {
        super (message, cause);
    }

    public IntegrationException (Throwable cause) {
        super (cause);
    }

    protected IntegrationException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super (message, cause, enableSuppression, writableStackTrace);
    }
}
