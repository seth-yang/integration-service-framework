package org.dreamwork.integration.internal;

import org.dreamwork.integration.api.IModule;
import org.dreamwork.integration.api.IModuleContext;
import org.dreamwork.integration.api.ModuleInfo;
import org.dreamwork.integration.api.services.IHttpdService;
import org.dreamwork.integration.internal.embedded.httpd.ApiServlet;
import org.dreamwork.integration.internal.embedded.httpd.ServiceScanner;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *  Created by fei on 2020/04/16
 */
public class EmbeddedHttpdService implements IHttpdService {
    private final Logger logger = LoggerFactory.getLogger (EmbeddedHttpdService.class);
    private final IConfiguration conf;
    private final Tomcat tomcat = new Tomcat ();
    private final ServerClassLoader scl = new ServerClassLoader();
    private final Map<String, Context> loadedContexts = Collections.synchronizedMap (new HashMap<>());

    /** @since 1.1.0 */
    private File webapps;

    EmbeddedHttpdService (IConfiguration conf) {
        this.conf = conf;
    }

    void start () throws Exception {
        if (logger.isTraceEnabled ()) {
            logger.trace ("starting embedded httpd server ...");
        }
        setupTomcat ();
        logger.info ("embedded httpd server started.");
    }

    void shutdown () throws Exception {
        tomcat.stop ();
        tomcat.destroy();
        tomcat.getServer().await();
    }

    private void setupTomcat () throws IOException, LifecycleException {
        int     http_port       = conf.getInt (KEY_HTTPD_PORT, 7777);
        boolean https_enabled   = conf.getBoolean (KEY_HTTPS_ENABLED, false);
        int     https_port      = conf.getInt (KEY_HTTPS_PORT, 7778);
        String  host            = conf.getString (KEY_HOST_ADDR);

        if (StringUtil.isEmpty (host)) {
            host = "127.0.0.1";
        }

        if (logger.isTraceEnabled ()) {
            logger.trace ("http port     = {}", http_port);
            logger.trace ("https enabled = {}", https_enabled);
            logger.trace ("https port    = {}", https_port);
            logger.trace ("service addr  = {}", host);
        }

        String temp_dir = System.getProperty ("java.io.tmpdir");
        File serverRoot = new File (temp_dir, "embedded-httpd/webapps/ROOT");
        if (!serverRoot.exists() && !serverRoot.mkdirs()) {
            throw new IOException ("can't create dirs");
        }

        serverRoot = new File (temp_dir, "embedded-httpd");
        tomcat.setBaseDir (serverRoot.getCanonicalPath ());
        tomcat.setPort (http_port);
        tomcat.setHostname (host);
        tomcat.getHost().setParentClassLoader(scl);
        tomcat.getConnector ();

        if (logger.isTraceEnabled()) {
            logger.trace ("Catalina Base = {}", tomcat.getServer().getCatalinaBase().getCanonicalPath());
        }

        Context root = tomcat.addContext ("", "ROOT");
        root.setParentClassLoader (scl);
        Wrapper wrapper = Tomcat.addServlet(root, "management-servlet", new ManageServlet());
        wrapper.addMapping ("/mgt/*");
        wrapper.addMapping ("/favicon.ico");
//        Tomcat.addServlet (root, "restful-api-mapping", new RestfulAPIServlet ()).addMapping ("/api/*");
//        ServletContext api = root.getServletContext ();

        tomcat.start ();
        // @since 1.1.0
        webapps = serverRoot.getParentFile ();
    }

///*
//    @Override
//    public boolean mapping (String pattern, Class<? extends Servlet> servlet) {
//        return mappingTomcat (pattern, servlet);
//    }
//
//    @Override
//    public void unmap(String pattern) {
//        root.removeServletMapping(pattern);
//    }
//
//    private boolean mappingTomcat (String pattern, Class<? extends Servlet> servlet) {
//        String name = null;
//        if (servlet.isAnnotationPresent (WebServlet.class)) {
//            WebServlet ann = servlet.getAnnotation (WebServlet.class);
//            name = ann.name ();
//        }
//        if (StringUtil.isEmpty (name)) {
//            name = StringUtil.uuid ();
//        }
//        Tomcat.addServlet (root, name, servlet.getCanonicalName());
//        root.addServletMappingDecoded (pattern, name);
//        try {
//            root.start();
//            return true;
//        } catch (Exception ex) {
//            logger.warn (ex.getMessage(), ex);
//            return false;
//        }
//    }
//
//    */
    /**
     * @param base        web app 的根目录
     * @param contextPath 映射的访问路径
     * @param loader      这个web应用使用的 classloader, 可以为 null， 此时将使用默认的 classloader
     */
    private StandardContext addWebApp (String base, String contextPath, ClassLoader loader) {
        synchronized (loadedContexts) {
            if (loadedContexts.containsKey(contextPath)) {
                throw new IllegalStateException("context[" + contextPath + "] already exist");
            }

            if (loader != null) {
                // 启动新webapp时，先扫个地，不扫地不行，原因未明!!!
                System.gc ();
                try {
                    Thread.sleep (200);
                } catch (InterruptedException ignore) {
                }
                // 以上代码不知道为什么就是能工作

                scl.addContext (contextPath, loader);
                String path;
                if (contextPath.charAt(0) == '/') {
                    path = contextPath;
                } else {
                    path = '/' + contextPath;
                }
                StandardContext app = (StandardContext) tomcat.addWebapp (path, base);
                loadedContexts.put(contextPath, app);
                return app;
//                return app.getServletContext ();
            }
        }

        return null;
    }
//
//    @Override
    public void removeWebApp(String contextPath) throws Exception {
        synchronized (loadedContexts) {
            if (loadedContexts.containsKey(contextPath)) {
                try {
                    Context ctx = loadedContexts.get(contextPath);
                    ctx.setParentClassLoader (null);
                    ctx.stop();
                    ctx.destroy();
                    tomcat.getHost ().removeChild (ctx);
                } finally {
                    scl.removeContext (contextPath);
                    loadedContexts.remove(contextPath);
                }
            }
        }
    }
//
//    @Override
//    public void addRestfulApi(String moduleName, InputStream in, ClassLoader classLoader) {
//        Map<String, APIDefinition> map= null;
//        try {
//            map = RestfulAPIManager.load (in, api,classLoader,moduleName);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        api.setAttribute (moduleName, map);
//        logger.debug("{} setConfig success",moduleName);
//    }
//
//    @Override
//    public void removeRestfulApi(String moduleName) {
//        RestfulAPIManager.unload (moduleName);
//        logger.info ("apis associated module[{}] removed", moduleName);
//    }
//
//    private Context createContext (String contextPath, String docBase) {
//        synchronized (loadedContexts) {
//            if (loadedContexts.containsKey(contextPath)) {
//                throw new IllegalStateException("context[" + contextPath + "] already exist");
//            }
//            Context ctx = tomcat.addContext(contextPath, docBase);
//            loadedContexts.put(contextPath, ctx);
//            return ctx;
//        }
//    }
//*/

    private static final String KEY_HOST_ADDR       = "http.service.addr";
    private static final String KEY_HTTPD_PORT      = "http.service.port";
    private static final String KEY_HTTPS_PORT      = "http.service.ssl.port";
    private static final String KEY_HTTPS_ENABLED   = "http.service.ssl.enabled";
//    private static final String KEY_CONTEXT_PATH    = "http.service.context";

    @Override
//    public void attach (ModuleInfo module, ClassLoader loader, Path workdir) {
    public void attach (IModuleContext context, Path workdir) {
        try {
            ModuleInfo module  = context.getInfo ();
            ClassLoader loader = context.getContextClassLoader ();
            // double check weather the module supports http-service
            if (!module.requireHttpd) {
                return;
            }

            String contextName = getContextPath (module);
            String root = workdir.toString (), contextDir;
            if (!StringUtil.isEmpty (module.basedir)) {
                contextDir = module.basedir;
            } else {
                contextDir = "web-app";
            }
            Path path = Paths.get (root, contextDir);
            if (!Files.exists (path)) {
                // 没指定，临时创建一个
                path = Paths.get (webapps.toString (), contextName);
                if (!Files.exists (path)) {
                    Files.createDirectories (path);
                }
            }

            Context ctx = addWebApp (path.toRealPath ().toString (), contextName, loader);
            if (ctx != null) {
                ServletContext app = ctx.getServletContext ();
                // 把集成容器的运行环境传播到他自己的web应用上
                app.setAttribute (IModule.CONTEXT_ATTRIBUTE_KEY, context);
                // 尝试加载 restful apis
                if (module.apiPackages != null && module.apiPackages.length > 0) {
                    ServiceScanner scanner = new ServiceScanner (context, loader, module.apiPackages);
                    // 扫描注解，加载 restful apis
                    scanner.scan ();
                    if (scanner.isNotEmpty ()) {
                        // 保存到 ServletContext 中
                        app.setAttribute (ServiceScanner.class.getCanonicalName (), scanner);
                        // 注册 Restful Api 处理 Servlet
                        Wrapper wrapper = Tomcat.addServlet (ctx, "restful-api", ApiServlet.class.getCanonicalName ());
                        wrapper.addMapping ("/apis/*");
                        logger.info ("embedded web app {} bound as {}/apis/*", contextName, contextName);
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }
    }

    @Override
    public void detach (ModuleInfo module) {
        try {
            String contextPath = getContextPath (module);
/*
            if (contextPath.charAt (0) == '/') {
                contextPath = contextPath.substring (1);
            }
*/
            removeWebApp (contextPath);
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }
    }

    private String getContextPath (ModuleInfo module) {
        String path = module.context;
        if (StringUtil.isEmpty (path)) {
            path = module.name;
        }
        if (path.charAt (0) != '/') {
            path = '/' + path;
        }

        return path;
    }
}