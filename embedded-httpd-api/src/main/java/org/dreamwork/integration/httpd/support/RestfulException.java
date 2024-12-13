package org.dreamwork.integration.httpd.support;

public class RestfulException extends RuntimeException {
    public int code;

    public RestfulException (int code) {
        this.code = code;
    }

    public RestfulException (String message, int code) {
        super (message);
        this.code = code;
    }

    public RestfulException (String message, Throwable cause, int code) {
        super (message, cause);
        this.code = code;
    }

    public RestfulException (Throwable cause, int code) {
        super (cause);
        this.code = code;
    }

    public RestfulException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, int code) {
        super (message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }

    public int getCode () {
        return code;
    }
}
