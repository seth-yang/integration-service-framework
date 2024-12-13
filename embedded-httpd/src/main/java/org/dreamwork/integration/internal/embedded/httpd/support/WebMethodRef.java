package org.dreamwork.integration.internal.embedded.httpd.support;

import org.dreamwork.integration.httpd.annotation.*;
import org.dreamwork.integration.httpd.support.ParameterLocation;
import org.dreamwork.integration.httpd.support.ParameterType;
import org.dreamwork.util.StringUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dreamwork.integration.httpd.support.ParameterLocation.*;
import static org.dreamwork.integration.httpd.support.ParameterType.*;

public class WebMethodRef {
    public static final Pattern PARSER = Pattern.compile ("^(.*?)?\\$\\{(.*?)}(.*?)?$");

    public Object bean;

    public final Method method;
    public final String pattern;
    public String contentType;

    public List<String> parts;

    public List<WebParamRef> parameters;
    /** @since 1.1.0 */
    public boolean wrapped;

    public WebMethodRef (Method method, String pattern) {
        this.method  = method;
        this.pattern = pattern;

        if (pattern.startsWith ("/")) {
            pattern = pattern.substring (1);
        }

        if (pattern.contains ("/")) {
            parts = split (pattern);
        }

        if (method == null) {
            throw new NullPointerException ("method");
        }

        if (method.getParameterCount () > 0) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations ();
            if (method.getParameterCount () != parameterAnnotations.length) {
                throw new IllegalArgumentException ("some parameter of method " + method + " not annotated.");
            }

            parameters = new ArrayList<> (parameterAnnotations.length);
            for (Annotation[] as : parameterAnnotations) {
                if (as.length > 0) {
                    for (Annotation an : as) {
                        if (an instanceof AWebParam) {
                            AWebParam awp = (AWebParam) an;
                            WebParamRef wp = new WebParamRef ();
                            String name = awp.name ();
                            if (StringUtil.isEmpty (name)) {
                                name = awp.value ();
                            }

                            if (StringUtil.isEmpty (name) && awp.location () != Body) {
                                throw new IllegalArgumentException ("property[name] of AWebParameter is missing!");
                            }
                            wp.name = name.trim ();
                            if (!StringUtil.isEmpty (awp.defaultValue ())) {
                                wp.defaultValue = awp.defaultValue ().trim ();
                            }
                            wp.location = awp.location ();
                            wp.type     = awp.type ();
                            parameters.add (wp);

                            break;
                        } else if (an instanceof ARequestBody) {
                            WebParamRef wp = new WebParamRef ();
                            wp.location = Body;
                            wp.contentType = "application/json;charset=utf-8";
                            parameters.add (wp);

                            break;
                        } else if (an instanceof ARequestAttribute) {
                            ARequestAttribute ra = (ARequestAttribute) an;
                            appendParameter (ra.name (), ra.value (), RequestAttribute);
                        } else if (an instanceof ASession) {
                            ASession sa = (ASession) an;
                            appendParameter (sa.name (), sa.value (), SessionAttribute);
                        } else if (an instanceof AHeader) {
                            // 从 http 头获取参数
                            AHeader ha = (AHeader) an;
                            appendParameter (ha.name (), ha.value (), Header);
                        } else if (an instanceof ACookie) {
                            // 从 cookie 获取参数
                            ACookie ca = (ACookie) an;
                            WebParamRef wp = appendParameter (ca.name (), ca.value (), Cookie);
                            wp.defaultValue = ca.defaultValue ();
                            wp.type = ca.type ();
                        } else if (an instanceof AContextAttribute) {
                            AContextAttribute ca = (AContextAttribute) an;
                            appendParameter (ca.name (), ca.value (), translateLocation (ca));
                        } else if (an instanceof AFormItem) {
                            AFormItem fi = (AFormItem) an;
                            WebParamRef wp = appendParameter (fi.name (), fi.value (), QueryString);
                            wp.defaultValue = fi.defaultValue ();
                            wp.type = fi.type ();
                        } else if (an instanceof APathVariable) {
                            APathVariable pv = (APathVariable) an;
                            WebParamRef wp = appendParameter (pv.name (), pv.value (), Path);
                            wp.type = pv.type ();
                        }
                    }
                } else {
                    WebParamRef wp = new WebParamRef ();
                    wp.internal = true;
                    wp.location = ParameterLocation.Auto;
                    parameters.add (wp);
                }
            }
        }
    }

    public ParameterLocation translateLocation (AContextAttribute ca) {
        switch (ca.scope ()) {
            case CONTEXT: return ContextAttribute;
            case COOKIE : return Cookie;
            case HEAD   : return Header;
            case REQUEST: return RequestAttribute;
            case SESSION: return SessionAttribute;
            default     : return Auto;
        }
    }

    public ParameterType translateType (ParameterLocation location) {
        switch (location) {
            case ContextAttribute:
                return context_attribute;
            case RequestAttribute:
                return request_attribute;
            case SessionAttribute:
                return session_attribute;
            default:
                return raw;
        }
    }

    /**
     * 添加参数
     * @param firstName  第一名称
     * @param secondName 第二名称
     * @since 1.1.0
     */
    private WebParamRef appendParameter (String firstName, String secondName, ParameterLocation location) {
        WebParamRef wp = new WebParamRef ();
        String name = firstName;
        if (StringUtil.isEmpty (name)) name = secondName;
        if (StringUtil.isEmpty (name)) {
            throw new IllegalArgumentException ("Parameter name not set");
        }
        wp.name = name;
        wp.location = location;
        wp.type = translateType (location);
        parameters.add (wp);
        return wp;
    }

    public boolean matches (String pattern, Map<String, String> parsedArgs) {
        if (parts != null && !parts.isEmpty ()) {
            List<String> tmp = split (pattern);
            if (tmp.size () != parts.size ()) {
                return false;
            }
            Map<String, String> map = new HashMap<> ();
            for (int i = 0, n = tmp.size (); i < n; i ++) {
                String name = parts.get (i);
                if (name.equals ("*") && i == n - 1) {
                    // 最后一个部分是 * , 则不再比较后续部分
                    return true;
                } else if (name.contains ("${")) {
                    Matcher m = PARSER.matcher (name);
                    if (m.matches ())
                        map.put (m.group (2), tmp.get (i));
                } else if (!name.equals (tmp.get (i))) {    // 任意一部分不匹配，返回false
                    return false;
                }
            }
            if (!map.isEmpty ()) {
                parsedArgs.putAll (map);
            }
            return true;
        } else {
            return pattern.equals (this.pattern);
        }
    }

    public Object invoke (Object... args) throws InvocationTargetException {
        try {
            return method.invoke (bean, args);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException (ex);
        }
    }

    private List<String> split (String pattern) {
        String[] tmp = pattern.split ("/");
        List<String> parts = new ArrayList<> (tmp.length);
        for (String p : tmp) {
            if (!StringUtil.isEmpty (p)) {
                parts.add (p.trim ());
            }
        }
        return parts;
    }
}
