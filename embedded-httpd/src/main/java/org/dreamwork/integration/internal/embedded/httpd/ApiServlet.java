package org.dreamwork.integration.internal.embedded.httpd;

import com.google.gson.Gson;
import org.dreamwork.integration.httpd.support.ResponseEntity;
import org.dreamwork.integration.httpd.support.RestfulException;
import org.dreamwork.integration.internal.embedded.httpd.support.WebJsonResult;
import org.dreamwork.integration.internal.embedded.httpd.support.WebMethodRef;
import org.dreamwork.integration.internal.embedded.httpd.support.WebParamRef;
import org.dreamwork.gson.GsonHelper;
import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

public class ApiServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger (ApiServlet.class);

    private ServiceScanner scanner;

    @Override
    public void init () throws ServletException {
        super.init ();

        ServletContext context = getServletContext ();
        scanner = (ServiceScanner) context.getAttribute (ServiceScanner.class.getCanonicalName ());
    }

    @Override
    protected void service (HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method   = request.getMethod ().toLowerCase ();
        String pathInfo = request.getPathInfo ();
        if (StringUtil.isEmpty (pathInfo)) {
            response.setStatus (SC_NOT_FOUND);
            return;
        }

        Map<String, String> values = new HashMap<> ();
        WebMethodRef ref = scanner.match (pathInfo, method, values);
        if (ref == null) {
            response.setStatus (SC_NOT_FOUND);
            return;
        }

        Object value;
        Gson g = GsonHelper.getGson ();
        try {
            if (ref.parameters == null || ref.parameters.isEmpty ()) {
                value = ref.invoke ();
            } else {
                Object[] args = parseParameters (request, response, ref, values);
                value = ref.invoke (args);
            }

            if (StringUtil.isEmpty (ref.contentType)) {
                ref.contentType = "application/json;charset=utf-8";
            }
            response.setContentType (ref.contentType);
            if (ref.contentType.contains ("json")) {
                if (ref.wrapped) {
                    WebJsonResult wjr = new WebJsonResult (0, "success", value);
                    g.toJson (wjr, response.getWriter ());
                } else if (value instanceof ResponseEntity) {
                    writeResponseEntity (response, (ResponseEntity<?>) value, g);
                } else if (value != null) {
                    g.toJson (value, response.getWriter ());
                }
            } else if (value instanceof ResponseEntity) {
                writeResponseEntity (response, (ResponseEntity<?>) value, g);
            } else if (value != null) {
                response.getWriter ().write (value.toString ());
            }
        } catch (InvocationTargetException ite) {
            Throwable t = ite.getCause ();
            logger.warn (t.getMessage (), t);
            if (ref.contentType.contains ("json")) {
                if (t instanceof RestfulException) {
                    handleRestfulException (response, (RestfulException) t, g);
                } else {
                    throw new ServletException (t);
                }
            } else {
                if (t instanceof RestfulException) {
                    RestfulException re = (RestfulException) t;
                    if (re.code >= 300 && re.code < 600) {
                        response.setStatus (re.code);
                    }
                    response.getWriter ().write (re.getMessage ());
                } else {
                    throw new ServletException (t);
                }
            }
        }
    }

    private void writeResponseEntity (HttpServletResponse response, ResponseEntity<?> entity, Gson g) throws IOException {
        response.setStatus (entity.getStatus ());
        Object o = entity.getData ();
        if (o != null) {
            response.getWriter ().write (g.toJson (o));
        } else if (!StringUtil.isEmpty (entity.getMessage ())) {
            response.getWriter ().write (entity.getMessage ());
        }
    }

    private void handleRestfulException (HttpServletResponse response, RestfulException re, Gson g) throws IOException {
        int code = re.getCode ();
        if (code >= 300 && code < 600) {
            response.setStatus (code);
        }
        WebJsonResult wjr = new WebJsonResult (code, re.getMessage (), null);
        g.toJson (wjr, response.getWriter ());
    }

    private Object[] parseParameters (HttpServletRequest request, HttpServletResponse response, WebMethodRef ref, Map<String, String> values) throws IOException {
        Object[] args = new Object[ref.parameters.size ()];
        Class<?>[] types = ref.method.getParameterTypes ();
        for (int i = 0; i < ref.parameters.size (); i ++) {
            WebParamRef wp = ref.parameters.get (i);
            Class<?> type = types [i];
            if (wp == null || wp.internal) {
                if (type == ServletContext.class) {
                    args[i] = getServletContext ();
                } else if (type == HttpServletRequest.class) {
                    args[i] = request;
                } else if (type == HttpServletResponse.class) {
                    args[i] = response;
                } else if (type == HttpSession.class) {
                    args[i] = request.getSession ();
                } else {
                    throw new IllegalArgumentException ("unsupported internal type: " + type);
                }
            } else {
                String temp;
                switch (wp.location) {
                    case QueryString:
                        temp = request.getParameter (wp.name);
                        if (StringUtil.isEmpty (temp) && isNotEmpty (wp.defaultValue)) {
                            temp = wp.defaultValue;
                        }
                        break;
                    case Body:
                        String contentType = request.getContentType ();
                        if (contentType.contains ("json")) {
                            temp = new String (IOUtil.read (request.getInputStream ()));
                        } else {
                            temp = request.getParameter (wp.name);
                        }
                        break;
                    case Path:
                        temp = values.get (wp.name);
                        break;
                    case Header:
                        temp = request.getHeader (wp.name);
                        if (StringUtil.isEmpty (temp) && isNotEmpty (wp.defaultValue)) {
                            temp = wp.defaultValue;
                        }
                        break;
                    // @since 1.1.0
                    case Internal:
                    case ContextAttribute:
                    case RequestAttribute:
                    case SessionAttribute:
                        temp = "0";
                        break;

                    case Cookie:
                        temp = getFromCookie (request, wp.name);
                        break;

                    default:
                        throw new IllegalArgumentException ("unknown location: " + wp.location);
                }

                Object o;
                if (wp.type == null) {
                    // 视为 raw
                    Class<?> parameterType = ref.method.getParameterTypes ()[i];
                    args[i] = translate (temp, parameterType);
                } else {
                    switch (wp.type) {
                        case string:
                            args[i] = temp;
                            break;

                        case integer:
                            args[i] = Integer.parseInt (temp);
                            break;

                        case long_integer:
                            args[i] = Long.parseLong (temp);
                            break;

                        case bool:
                            args[i] = Boolean.parseBoolean (temp);
                            break;

                        case datetime:
                            try {
                                args[i] = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss").parse (temp);
                            } catch (ParseException pe) {
                                try {
                                    args[i] = new SimpleDateFormat ("yyyy-MM-dd").parse (temp);
                                } catch (ParseException ex) {
                                    throw new RuntimeException (ex);
                                }
                            }
                            break;

                        case raw:
                            Class<?> parameterType = ref.method.getParameterTypes ()[i];
                            args[i] = translate (temp, parameterType);
                            break;
                        // @since 1.1.0
                        case request_attribute:
                            o = request.getAttribute (wp.name);
                            if (o == null && !wp.nullable) {
                                throw new RuntimeException ("parameter [request." + wp.name + "] needs value, but meet null!");
                            }
                            args[i] = o;
                            break;
                        // @since 1.1.0
                        case session_attribute:
                            o = request.getSession ().getAttribute (wp.name);
                            if (o == null && !wp.nullable) {
                                throw new RuntimeException ("parameter [session." + wp.name + "] needs value, but meet null!");
                            }
                            args[i] = o;
                            break;
                        case context_attribute:
                            o = request.getServletContext ().getAttribute (wp.name);
                            if (o == null && !wp.nullable) {
                                throw new RuntimeException ("parameter [session." + wp.name + "] needs value, but meet null!");
                            }
                            args[i] = o;
                            break;
                    }
                }
            }
        }
        return args;
    }

    private String getFromCookie (HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies ();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals (cookie.getName ())) {
                    return cookie.getValue ();
                }
            }
        }
        return "";
    }

    private Object translate (String expression, Class<?> type) {
        if (type == int.class || (type == Integer.class && !StringUtil.isEmpty (expression))) {
            return Integer.parseInt (expression);
        }

        if (type == byte.class || (type == Byte.class && !StringUtil.isEmpty (expression))) {
            return Byte.parseByte (expression);
        }
        if (type == char.class || (type == Character.class && !StringUtil.isEmpty (expression))) {
            return expression.isEmpty () ? '\u0000' : expression.charAt (0);
        }
        if (type == short.class || (type == Short.class && !StringUtil.isEmpty (expression))) {
            return Short.parseShort (expression);
        }
        if (type == long.class || (type == Long.class && !StringUtil.isEmpty (expression))) {
            return Long.parseLong (expression);
        }
        if (type == boolean.class || (type == Boolean.class && !StringUtil.isEmpty (expression))) {
            return Boolean.parseBoolean (expression);
        }
        if (type == float.class || (type == Float.class && !StringUtil.isEmpty (expression))) {
            return Float.parseFloat (expression);
        }
        if (type == double.class || (type == Double.class && !StringUtil.isEmpty (expression))) {
            return Double.parseDouble (expression);
        }
        if (type.isAssignableFrom (String.class)) {
            return expression;
        }
        if (type == BigDecimal.class && !StringUtil.isEmpty (expression)) {
            return new BigDecimal (expression);
        }
        if (type == BigInteger.class && !StringUtil.isEmpty (expression)) {
            return new BigInteger (expression);
        }
        if (type == Date.class && !StringUtil.isEmpty (expression)) {
            return toDate (expression);
        }
        if (type == java.sql.Date.class && !StringUtil.isEmpty (expression)) {
            return new java.sql.Date (toDate (expression).getTime ());
        }
        if (type == java.sql.Timestamp.class && !StringUtil.isEmpty (expression)) {
            return new java.sql.Timestamp (toDate (expression).getTime ());
        }
        return StringUtil.isEmpty (expression) ? null : GsonHelper.getGson ().fromJson (expression, type);
    }

    private Date toDate (String expression) {
        try {
            return new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss").parse (expression);
        } catch (ParseException ex) {
            try {
                return new SimpleDateFormat ("yyyy-MM-dd").parse (expression);
            } catch (ParseException e) {
                throw new RuntimeException (e);
            }
        }
    }

    private boolean isEmpty (String value) {
        return StringUtil.isEmpty (value) || "$$EMPTY$$".equals (value);
    }

    private boolean isNotEmpty (String value) {
        return !isEmpty (value);
    }
}