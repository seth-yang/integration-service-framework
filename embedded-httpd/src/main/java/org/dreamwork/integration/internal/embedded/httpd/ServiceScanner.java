package org.dreamwork.integration.internal.embedded.httpd;

import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.httpd.annotation.ARestfulAPI;
import org.dreamwork.integration.internal.embedded.httpd.support.WebMethodRef;
import org.dreamwork.util.CollectionCreator;
import org.dreamwork.util.ResourceUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ServiceScanner {
    private final Logger logger = LoggerFactory.getLogger (ServiceScanner.class);
    private final ClassLoader loader;
    private final Set<String> packages;
    private final IModuleContext context;

    private final Map<String, Map<String, WebMethodRef>> methods = new HashMap<> ();

    public ServiceScanner (IModuleContext context, ClassLoader loader, String... packages) {
        this.context  = context;
        this.loader   = loader;
        this.packages = CollectionCreator.asSet (packages);
    }

    public void scan () throws Exception {
        for (String packageName : this.packages) {
            List<Class<?>> classes = ResourceUtil.getClasses (packageName, loader);
            for (Class<?> type : classes) {
                if (type.isAnnotationPresent (ARestfulAPI.class)) {
                    scanClass (type);
                }
            }
        }
    }

    public boolean isNotEmpty () {
        return !methods.isEmpty ();
    }

    public WebMethodRef match (String pathInfo, String method, Map<String, String> parsedArgs) {
        Map<String, WebMethodRef> map = methods.get (method);
        if (map == null) {
            return null;
        }

        if (map.containsKey (pathInfo)) {
            return map.get (pathInfo);
        }

        for (WebMethodRef ref : map.values ()) {
            if (ref.matches (pathInfo, parsedArgs)) {   // 更复杂的情况
                return ref;
            }
        }

        return null;
    }

    private void scanClass (Class<?> type) {
        ARestfulAPI restful = type.getAnnotation (ARestfulAPI.class);
        String[] temp = restful.urlPattern ();
        String category = null;
        if (temp.length > 0) {
            category = StringUtil.isEmpty (temp[0]) ? null : temp[0].trim ();
        }
        if (StringUtil.isEmpty (category)) {
            temp = restful.value ();
            if (temp.length > 0) {
                category = StringUtil.isEmpty (temp[0]) ? "" : temp[0].trim ();
            }
        }

        // 只能是托管对象
        Object bean = context.findService (type);
        if (bean == null) {
            logger.error ("web handler {} was not managed.", type);
            return;
        }
/*
        if (bean == null) {
            try {
                Constructor<?> constructor = type.getConstructor (IModuleContext.class);
                bean = constructor.newInstance (context);
            } catch (NoSuchMethodException | SecurityException ex) {
                if (logger.isTraceEnabled ()) {
                    // 明细模式下才打印这个堆栈
                    logger.warn (ex.getMessage (), ex);
                }
                logger.warn ("there is no constructor with parameter IModuleContext, using default constructor");
            }

            if (bean == null) {
                bean = type.newInstance ();
            }


        }
*/

        Method[] methods = type.getMethods ();
        for (Method method: methods) {
            if (method.isAnnotationPresent (ARestfulAPI.class)) {
                ARestfulAPI api = method.getAnnotation (ARestfulAPI.class);
                String httpMethod = api.method ();
                if (StringUtil.isEmpty (httpMethod)) {
                    httpMethod = "get";
                }
                httpMethod = httpMethod.toLowerCase();
                String[] patterns = getUrlPatterns (api);
                if (patterns.length == 0) {
                    patterns = new String[] {"/"};
                }
/*


                String[] patterns = api.urlPattern ();
                if (patterns.length == 0) {
                    patterns = api.value ();
                }
*/

                for (String pattern : patterns) {
                    if (StringUtil.isEmpty (pattern)) {
                        pattern = "/";
                    }

                    String pathInfo = '/' + category + '/' + pattern;
                    while (pathInfo.contains ("//")) {
                        pathInfo = pathInfo.replace ("//", "/");
                    }
                    if (pathInfo.endsWith ("/")) {
                        pathInfo = pathInfo.substring (0, pathInfo.length () - 1);
                    }
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("method: {} mapping to {}", method, pathInfo);
                    }
                    WebMethodRef wmm = new WebMethodRef (method, pathInfo);
                    wmm.bean         = bean;
                    wmm.contentType  = api.contentType ();
                    wmm.wrapped      = api.wrapped ();

                    Map<String, WebMethodRef> map = this.methods.computeIfAbsent (httpMethod, name -> new HashMap<> ());
                    if (map.containsKey (pathInfo)) {
                        throw new IllegalArgumentException ("pattern " + pathInfo + " already mapped.");
                    }
                    map.put (pathInfo, wmm);
                    if (logger.isTraceEnabled ()) {
                        logger.trace (
                                "a web mapped method is mapped: [{}] {} <=> {}",
                                httpMethod.toUpperCase (), pathInfo, wmm.method
                        );
                    }
                }
            }
        }
    }

    private String[] getUrlPatterns (ARestfulAPI api) {
        Set<String> set = new HashSet<> ();
        set.addAll (Arrays.asList (api.urlPattern ()));
        set.addAll (Arrays.asList (api.value ()));
        set = set.stream ().filter (p -> !StringUtil.isEmpty (p)).collect(Collectors.toSet());
        return set.toArray (new String[0]);
    }
}