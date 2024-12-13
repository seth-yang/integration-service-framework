package org.dreamwork.integration.internal.embedded.httpd.support;

public class WebJsonResult {
    public int code;
    public String message;
    public Object result;

    public WebJsonResult () {
    }

    public WebJsonResult (int code, String message, Object result) {
        this.code = code;
        this.message = message;
        this.result = result;
    }
}