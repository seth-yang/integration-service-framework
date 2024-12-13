package org.dreamwork.integration.httpd.support;

import java.io.Serializable;

public class WebResult implements Serializable {
    public int code;
    public String message;
    public Object payload;

    public WebResult () {
    }

    public WebResult (int code, String message) {
        this.code = code;
        this.message = message;
    }

    public WebResult (Object data) {
        this.payload = data;
    }
}