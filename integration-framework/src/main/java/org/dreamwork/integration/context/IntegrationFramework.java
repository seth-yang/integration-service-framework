package org.dreamwork.integration.context;

import org.dreamwork.integration.api.*;
import org.dreamwork.integration.api.annotation.AModule;
import org.dreamwork.integration.api.services.IDatabaseService;
import org.dreamwork.integration.api.services.IHttpdService;
import org.dreamwork.integration.api.services.IMqttService;
import org.dreamwork.integration.api.services.ISystemService;
import org.dreamwork.integration.internal.DataSourceModule;
import org.dreamwork.integration.internal.DiscoveryModule;
import org.dreamwork.integration.services.IFrameworkService;
import org.dreamwork.integration.services.impl.DataSourceService;
import org.dreamwork.integration.services.impl.SystemServiceImpl;
import org.dreamwork.integration.util.Helper;
import org.dreamwork.integration.util.ModuleDeployer;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.concurrent.broadcast.ILocalBroadcastService;
import org.dreamwork.concurrent.broadcast.LocalBroadcaster;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.util.CollectionCreator;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.dreamwork.integration.context.ClassScannerHelper.fillPackageNames;
import static java.nio.file.StandardOpenOption.*;

/*

目录结构
/bin
/conf
/conf.d
/libs
/internal/*.jar
/extServices/*.jar
/modules/module_a/libs,module_b
/logs
/tmp  --> System.getProperty ("java.io.tmpdir")
----------/modules/*.zip解开后的结构---------------
/conf
/libs/*.jar
/web-app/
classes/META-INF/

 */
public class IntegrationFramework implements IntegrationFrameworkMBean, IMBeanDelegator, IModuleContextHandler {
    private static final String JMX_GROUP_NAME = "org.dreamwork.integration";
    private static final Predicate<Path> filter = f ->
            /*Files.isRegularFile (f) && */f.toString ().endsWith (".jar");

    private final Logger logger = LoggerFactory.getLogger (IntegrationFramework.class);
    private final PropertyConfiguration configuration;
    private final Map<String, ModuleContextImpl> contexts = new HashMap<> ();
    private final Set<ObjectName> names = new HashSet<> ();
    private final SimpleServiceRouter router = new SimpleServiceRouter ();
    private final List<IModuleListener> listeners = new ArrayList<> ();
    private final String temp_dir, extra_dir, extServices_dir;
    private final String[] module_dirs;

    private boolean jmxEnabled = false;
    private transient boolean stopped = false;
    private ExecutorService executor;
    private MBeanServer mBeanServer;
    private ServiceClassLoader serviceClassLoader;
    private Map<String, ModuleInfo> buildIns;
    private Map<String, ModuleInfo> loadedModules;
    private final Map<String, ModuleInfo> allModules = Collections.synchronizedMap (new HashMap<>());
    private ModuleInfo debuggingModule;

    private LocalBroadcaster broadcaster;

    private DatagramSocket socket;

    public static final String VERSION = "1.1.0";
    public static final String PORT_FILE_NAME = ".port-number";

//    private final static Map<String, String> BUILD_IN_MODULES = new HashMap<> ();
    private final static Map<String, String> BUILD_IN_MODULES = CollectionCreator.asMap (
        "discovery", DiscoveryModule.class.getCanonicalName (),
        "database-provider", DataSourceModule.class.getCanonicalName (),
        "embedded-httpd", "org.dreamwork.integration.internal.EmbeddedHttpdModule",
        "embedded-mqtt", "org.dreamwork.embedded.mqtt.EmbeddedMqttModule",
        "embedded-redis", "org.dreamwork.embedded.redis.EmbeddedRedisModule"
    );
//    private final static Map<String, String> MODULE_MEMO      = new HashMap<> ();
    private final static Map<String, String> MODULE_MEMO = CollectionCreator.asMap (
        "discovery", "端口发现服务",
        "database-provider", "数据库服务提供程序，自动加载模块的数据库配置",
        "embedded-httpd", "内置的 httpd 服务，可自动装配各个应用模块内配置 Web Servlet 组件 和/或 Restful 接口",
        "embedded-mqtt", "内置的 mqtt 连接服务，统一管理 mqtt 连接配置并提供 mqtt 连接的懒加载服务"
    );
    private final static String[] data_config_names = {
            "database.conf",
            "database.properties",
            "database.xml"
    };

    private final Thread hook = new Thread (() -> {
        try {
            shutdown ();
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }
    });

    public IntegrationFramework (PropertyConfiguration configuration) {
        this.configuration = configuration;

        extra_dir = configuration.getString ("ext.conf.dir");
        temp_dir  = configuration.getString ("framework.tmp.dir");
        extServices_dir  = configuration.getString ("framework.extServices.dir");

        String module_dir = configuration.getString ("framework.modules.dir");
        List<String> dirs = new ArrayList<> ();
        dirs.add ("../modules");

        if (!StringUtil.isEmpty (module_dir)) {
            String[] tmp = module_dir.split (File.pathSeparator);
            for (String p : tmp) {
                if (!StringUtil.isEmpty (p)) {
                    p = p.trim ();
                    if (!dirs.contains (p))
                        dirs.add (p);
                }
            }
        }
        module_dirs = dirs.toArray (new String[0]);
    }

    public void startup () throws Exception {
        if (logger.isTraceEnabled ()) {
            logger.trace ("starting integration context");
        }

        long start = System.currentTimeMillis ();
        try {
            // 为框架启动做准备
            prepare ();

            executor = Executors.newCachedThreadPool ();
            broadcaster = new LocalBroadcaster (executor);
            executor.execute (broadcaster);

            // 注册系统数据库服务
            ModuleContextImpl dummy = new ModuleContextImpl ();
            dummy.setName ("$$dummy$$");
            router.registerService (dummy, ISystemService.class, new SystemServiceImpl (configuration));
            router.registerService (dummy, IFrameworkService.class, this);
            router.registerService (dummy, ILocalBroadcastService.class, broadcaster);

            // 启动内部模块
            startBuildInModule ();

            // 加载各个外部模块导出的服务
            loadExportedServices ();

            // 加载外部模块
            loadExtModules ();

            // 如果指定的调试模块，则该模块应该是最后被加载，不会被其他模块所依赖
            if (debuggingModule != null) {
                startModule (debuggingModule, getClass ().getClassLoader (), null, true);
            }

            // 启动停止服务 udp 端口
            listenShutdownService ();
        } finally {
            long now = System.currentTimeMillis ();
            logger.info ("Hothink Integration Framework started, it takes {} ms.", now - start);
        }

        Runtime.getRuntime ().addShutdownHook (hook);

        synchronized (JMX_GROUP_NAME) {
            JMX_GROUP_NAME.wait ();
        }
    }

    public void shutdown () {
        if (stopped) {
            return;
        }
        if (mBeanServer != null) {
            for (ObjectName name : names) {
                try {
                    mBeanServer.unregisterMBean (name);
                } catch (MBeanRegistrationException | InstanceNotFoundException ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }

            mBeanServer = null;
        }

        if (!contexts.isEmpty ()) {
            // 这里算法有点问题，依赖次数并不代表着依赖层次深！！！
            // 排序，先停不被依赖的模块
            Map<String, Wrapper> wrappers = contexts.values ().stream ().map (c -> {
                ModuleInfo info = c.getInfo ();
                if (info.internal) {
                    return new Wrapper (info.name, Integer.MAX_VALUE);
                } else if ("framework-manager".equals (info.name)) {
                    return new Wrapper (info.name, Integer.MAX_VALUE >> 1);
                } else {
                    return new Wrapper (info.name, 0);
                }
            }).collect (Collectors.toMap (i -> i.name, i -> i));

            // 分析每个模块的依赖情况
            for (ModuleContextImpl context : contexts.values ()) {
                ModuleInfo info = context.getInfo ();
                if (!info.internal) {
                    if (!info.dependencies.isEmpty ()) {
                        info.dependencies.keySet ().forEach (name -> {
                            Wrapper w = wrappers.get (name);
                            if (w.count != Integer.MAX_VALUE) {
                                w.count ++;
                            }
                        });
                    }
                }
            }
            List<Wrapper> orders = new ArrayList<> (wrappers.values ());
            orders.sort (Comparator.comparingInt ((Wrapper a) -> a.count).thenComparing (a -> a.name));
            if (logger.isTraceEnabled ()) {
                logger.trace ("after sort: {}", orders);
            }

            for (Wrapper w : orders) {
                try {
                    ModuleContextImpl context = contexts.get (w.name);
                    synchronized (this) {
                        if (!listeners.isEmpty ()) {
                            for (IModuleListener lsn : listeners) {
                                lsn.onContextDestroy (context);
                            }
                        }
                    }

                    stopModule (context.getName ());
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }

            contexts.clear ();
        }

        if (broadcaster != null) {
            broadcaster.shutdownNow ();
        }

        if (executor != null) {
            executor.shutdownNow ();
        }

        Looper.exit ();

        synchronized (JMX_GROUP_NAME) {
            JMX_GROUP_NAME.notifyAll ();
        }

        stopped = true;
        logger.info ("all jobs done. exit the application");
        System.exit (0);
    }

    public void registerMBean (String name, Object instance) throws Exception {
        if (jmxEnabled) {
            ObjectName oName = new ObjectName (JMX_GROUP_NAME, "name", name);
            mBeanServer.registerMBean (instance, oName);
            names.add (oName);
        }
    }

    @Override
    public void unregisterMBean (String name) throws Exception {
        ObjectName oName = new ObjectName (JMX_GROUP_NAME, "name", name);
        try {
            mBeanServer.unregisterMBean (oName);
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        } finally {
            names.remove (oName);
        }
    }

    /**
     * 加载模块
     *
     * @param info   模块信息
     * @param loader 类加载器
     * @param workdir 模块工作目录
     * @param debug   是否正在调试的模块
     * @throws IntegrationException 无
     */
    public void startModule (ModuleInfo info, ClassLoader loader, Path workdir, boolean debug) throws IntegrationException {
        try {
            String moduleName = info.name;
            String className  = info.impl;

            if (logger.isTraceEnabled()) {
                logger.trace ("trying to start module: {} with class: {}", moduleName, className);
            }

            // 检查依赖的模块
            if (!info.dependencies.isEmpty ()) {
                for (ModuleInfo mi : info.dependencies.values ()) {
                    if (!contexts.containsKey (mi.name)) {
                        String message = "depended module [" + mi.name + "] not found!";
                        logger.warn (message);
                        throw new IntegrationException (message);
                    }
                }
            }

            ModuleContextImpl context = new ModuleContextImpl ();
            context.setInfo (info);
            Class<?> type = loader.loadClass (className);
            if (!IModule.class.isAssignableFrom (type)) {
                throw new ClassCastException ("can't cast " + type.getCanonicalName () + " to " + IModule.class.getCanonicalName ());
            }

            if (logger.isTraceEnabled ()) {
                logger.trace ("starting module {}, implementation: {} ...", moduleName, className);
            }
            IModule module = (IModule) type.newInstance ();

            context.setMbeanDelegation (this);
            context.setServiceRouter (router);
            context.setHandler (this);
            context.setName (moduleName);
            context.setWorkdir (workdir);
            context.setClassLoader (loader);
            context.setInstance (module);
            if (loader instanceof ModuleClassLoader) {
                ((ModuleClassLoader) loader).setModuleContext (context);
            }
            findConfig (moduleName, context, loader, className);

            // 异步启动模块，同时检测启动时长，若超出配置时长，强制结束启动过程
            int timeout = configuration.getInt ("integration.startup.timeout", 30000);

            // 查找数据库配置，如果可能
            configDatabase (context, moduleName, className, workdir == null, debug);
            // 如果可能，自动配置 mqtt
            configMqtt (context, moduleName);
            // 如果可能，自动装配 redis

            ModuleStartupTask task = new ModuleStartupTask (module, context);
            if (new StartupMonitor (executor, timeout).timing (task)) {
                String mBeanName = module.getMBeanName ();
                if (jmxEnabled && !StringUtil.isEmpty (mBeanName)) {
                    registerMBean (mBeanName, module);
                }

                contexts.put (moduleName, context);

                // @since 1.1.0
                if (info.requireHttpd) {
                    // webapp 和 restful api 的自动装配都将发生在这里
                    IHttpdService service = router.findService (IHttpdService.class);
                    if (service == null) {
                        logger.warn ("no httpd service present, ignore the web part of module {}", info.name);
                    } else {
                        service.attach (context, workdir);
                    }
                }

                synchronized (this) {
                    if (!listeners.isEmpty ()) {
                        for (IModuleListener lsn : listeners) {
                            lsn.onContextStartup (context);
                        }
                    }
                }
                info.running = true;
                allModules.put (moduleName, info);
                logger.info ("module [{}] start success!", moduleName);
            } else {
                logger.warn ("module [{}] start failed. trying to clean resources associated to it", moduleName);
                try {
                    stopModule (moduleName);
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        } catch (Throwable ex) {
            logger.warn ("module [{}] start failed!", info.name);
            logger.warn ("    module.info = {}", info);
            logger.warn (ex.getMessage (), ex);
            throw new IntegrationException (ex);
        }
    }

    private static void autoWare (ModuleContextImpl context, IModule module) throws IOException {
        Logger logger = LoggerFactory.getLogger (IntegrationFramework.class);

        Class<? extends IModule> type = module.getClass ();
        SimpleServiceRouter router = context.getServiceRouter ();
        ContextClassScanner scanner = new ContextClassScanner (context, router);

        if (type.isAnnotationPresent (AModule.class)) {
            AModule am = type.getAnnotation (AModule.class);
            Set<String> packages = new HashSet<> ();
            fillPackageNames (type, am, context.getContextClassLoader (), packages);

            if (!packages.isEmpty ()) {
                try {
                    router.setResolved (false);
                    scanner.scan (packages.toArray (new String[0]));
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                    throw new RuntimeException (ex);
                } finally {
                    router.setResolved (true);
                }
            }
        }
    }

    /**
     * 启动一个指定名称的模块.
     * <p>这个模块必须已经发布，参见 {@link #deploy(String)}</p>
     * @param name 模块名称
     * @throws IntegrationException 启动过程中可能抛出的异常
     */
    public void startModule (String name) throws IntegrationException {
        if (debuggingModule != null && debuggingModule.name.equals (name)) {
            throw new IntegrationException ("cannot dynamically start debugging module [" + name + "].");
        }

        if (module_dirs != null) {
            for (String dir : module_dirs) {
                try (Stream<Path> stream = Files.list (Paths.get (dir))) {
                    for (Iterator<Path> i = stream.iterator (); i.hasNext (); ) {
                        Path p = i.next ();
                        if (Files.exists (p) && p.endsWith (name)) {
//                        if (Files.isDirectory (p) && p.endsWith (name)) {
                            List<URL> urls = new ArrayList<> ();
                            appendLibraries (urls, p);
                            if (!urls.isEmpty ()) {
                                ModuleClassLoader loader = new ModuleClassLoader (name, urls.toArray (new URL[0]), serviceClassLoader);
                                Map<String, ModuleInfo> map = mapModules (loader, false);
                                if (!map.isEmpty ()) {
                                    extract (map);

                                    if (loadedModules == null) {
                                        loadedModules = new HashMap<> ();
                                    }
                                    loadedModules.putAll (map);

                                    ModuleInfo mi = map.values ().iterator ().next ();
                                    // 调试模块不允许动态启动，所以这里肯定不是调试启动
                                    startModule (mi, loader, p, false);

                                    Map<String, ModuleInfo> mapped = new HashMap<> ();
                                    ModuleInfo copied = Helper.copy (mi, mapped);
                                    if (!mapped.isEmpty ()) {
                                        copied.dependencies.putAll (mapped);
                                    }
                                    broadcaster.broadcast (IModule.ACTION_CONTEXT_EVENT, IModule.CODE_CONTEXT_STARTED, copied);
                                }
                            }
                            break;
                        }
                    }
                } catch (IOException | IntegrationException ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        }
    }

    private void appendLibraries (List<URL> urls, Path p) throws IOException {
        try (Stream<Path> jars = Files.list (Paths.get (p.toString (), "libs")).filter (filter)) {
            jars.forEach (f -> {
                try {
                    urls.add (f.toUri ().toURL ());
                } catch (MalformedURLException ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            });
        }
    }

    /**
     * 停止指定名称的模块
     * @param name 模块名称
     */
    public void  stopModule (String name) throws IntegrationException {
        if (debuggingModule != null && debuggingModule.name.equals (name)) {
            throw new IntegrationException ("cannot dynamically stop debugging module [" + name + "].");
        }

        ModuleContextImpl context = contexts.get (name);
        if (context != null) {
            try {
                // 通知模块停止
                synchronized (this) {
                    if (!listeners.isEmpty ()) {
                        for (IModuleListener lsn : listeners) {
                            try {
                                lsn.onContextDestroy (context);
                            } catch (Exception ex) {
                                logger.warn (ex.getMessage (), ex);
                            }
                        }
                    }
                }

                // 反注册MBean
                IModule module = context.getInstance ();
                if (module != null && !StringUtil.isEmpty (module.getMBeanName ())) {
                    try {
                        unregisterMBean (module.getMBeanName ());
                    } catch (Exception ignore) {}
                }

                try {
                    // 销毁模块，及其classloader
                    context.destroy ();
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            } finally {
                // 删除缓存
                contexts.remove (name);
                loadedModules.remove (name);

                allModules.get (name).running = false;
                broadcaster.broadcast (IModule.ACTION_CONTEXT_EVENT, IModule.CODE_CONTEXT_STOPPED, name);
                System.gc();
                logger.info ("module [{}] stopped", name);
            }
        }
    }

    @Override
    public void unloadServiceClassLoader () {
    }

    @Override
    public void loadServiceClassLoader () {
    }

    public ModuleInfo getModuleInfo2 (String name) {
        ModuleContextImpl context = contexts.get(name);
        return context == null ? null : context.getInfo();
    }

    /**
     * 查询指定名称的模块信息
     *
     * @param moduleName 模块名称
     * @return 如果模块存在，返回模块的详细信息，否则返回 null
     */
    @Override
    public Map<String, Object> getModuleInfo (String moduleName) {
        if (!allModules.containsKey (moduleName)) {
            return null;
        }

        Map<String, Object> map = new HashMap<> ();
        ModuleInfo info = allModules.get (moduleName);
        map.put ("name", moduleName);
        if (info.internal) {
            map.put ("type", "internal");
        }
        if (info.dependencies != null && !info.dependencies.isEmpty ()) {
            Set<String> set = new HashSet<> (info.dependencies.keySet ());
            map.put ("dependencies", set);
        }

        return map;
    }

    /**
     * 获取数据库配置名称集合
     *
     * @return 所有已注册在框架内的数据库配置名称
     */
    @Override
    public Set<String> getDatabaseConfigs () {
        DataSourceService service = (DataSourceService) router.findService (IDatabaseService.class);
        if (service != null) {
            return service.getConfigNames ();
        }

        return null;
    }

    private void initJMX () throws Exception {
        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        registerMBean ("framework", this);
    }

    private void startBuildInModule () throws IntegrationException, IOException {
        // 查找所有定义的内置模块
        Map<String, ModuleInfo> allBuildIns = mapBuildInModules ();

        // 检查是否指定了调试模块
        String debugModule = configuration.getString ("framework.debug.module");
        if (!StringUtil.isEmpty (debugModule)) {
            debugModule = debugModule.trim ();
            // 如果指定了调试模块，它会被默认的classloader找到，应该从内置模块列表中剔除
            debuggingModule = allBuildIns.get (debugModule);
            allBuildIns.remove (debugModule);
        }

        // 获取配置文件中需要加载的模块名称
        String enabled_build_in_modules = configuration.getString ("integration.build.in.modules");
        String[] build_in_modules;
        if (!StringUtil.isEmpty (enabled_build_in_modules)) {
            build_in_modules = enabled_build_in_modules.split ("[\\s,]");
            if (build_in_modules.length > 0) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("trying to load build-in module: {}", enabled_build_in_modules);
                }
                buildIns = new HashMap<> (build_in_modules.length);

                // 将需要激活的内建模块复制到 buildIns
                for (String module : build_in_modules) {
                    if (StringUtil.isEmpty (module)) {
                        continue;
                    }
                    if (allBuildIns.containsKey (module)) {
                        buildIns.put (module, allBuildIns.get (module));
                    } else {
                        logger.warn ("active build-in module [{}] not found", module);
                    }
                }
            }
        }

        // 清除临时缓存
        allBuildIns.clear ();
        if (buildIns != null && !buildIns.isEmpty ()) {
            extract (buildIns);

            List<ModuleInfo> ordered = Helper.order (buildIns);
            if (logger.isTraceEnabled ()) {
                logger.trace ("ordered build-in modules: {}", ordered);
            }

            for (ModuleInfo mi : ordered) {
                startModule (mi, getClass ().getClassLoader (), null, false);
            }

            allModules.putAll (buildIns);
        }
    }

    @Override
    public synchronized void addModuleListener (IModuleListener listener) {
        listeners.add (listener);
    }

    @Override
    public synchronized void removeModuleListener (IModuleListener listener) {
        listeners.remove (listener);
    }

    @Override
    public String deploy (String src) throws IOException {
        String moduleName = new ModuleDeployer (this)
                .deploy (new File (src), new File ("../modules"));
        allModules.put (moduleName, new ModuleInfo (moduleName, null, false));
        broadcaster.broadcast (IModule.ACTION_CONTEXT_EVENT, IModule.CODE_CONTEXT_DEPLOYED, moduleName);
        return moduleName;
    }

    @Override
    public void unDeploy(String moduleName) throws IOException {
        File target = new File ("../modules/", moduleName);
        if (!target.isDirectory ()) {
            throw new IOException ("module[" + moduleName + "] cannot be un-deploy dynamically");
        }
        allModules.remove (moduleName);
        broadcaster.broadcast (IModule.ACTION_CONTEXT_EVENT, IModule.CODE_CONTEXT_REMOVED, moduleName);
        ModuleDeployer.delete (target.toPath());
    }

    @Override
    public Set<String> getContextNames () {
        return new HashSet<> (contexts.keySet ());
    }

    @Override
    public Set<String> getServiceNames () {
        return new HashSet<> (router.getServiceNames ());
    }

    @Override
    @Deprecated
    public Object findService (String name) {
        return router.findService (name);
    }

    /**
     * 查找模块的配置信息
     * <ol>
     *     <li>首先检查 extra 下是否存在同名配置文件</li>
     *     <li>检查模块工作目录下的 conf 目录下是否存在同名的配置配置文件</li>
     *     <li>搜索类路径下的 /META-INF/ 是否存在同名配置文件</li>
     *     <li>搜索类路径下的 / 是否存在同名配置文件</li>
     *     <li>搜索类路径下，模块实现类所在包下是否存在同名配置文件</li>
     * </ol>
     * @param name       资源名称
     * @param context    模块上下文环境
     * @param loader     模块的 classloader
     * @param className  模块的实现名
     * @param searchExtra 是否搜索扩展配置目录
     * @throws IOException io exception
     */
    private URL findResource (String name, ModuleContextImpl context, ClassLoader loader, String className, boolean searchExtra, boolean debug) throws IOException {
        URL res = null;
        if (logger.isTraceEnabled ()) {
            logger.trace ("[{}] search extra = {}, debug = {}", context.getName (), searchExtra, debug);
        }
        if (searchExtra && !debug) {    // 不是调试模块，且搜索扩展配置
            Path extra = Paths.get (extra_dir);
            // 寻找合适的配置信息
            if (Files.exists (extra)) {
                // 如果扩展配置目录存在，从扩展配置目录中查找和模块同名的配置文件
                Path conf = Paths.get (extra.toString (), name);
                if (Files.exists (conf)) {
                    // 如果该文件存在
                    res = conf.toUri ().toURL ();
                } else if (logger.isTraceEnabled()) {
                    logger.trace("[{}] resource file [{}] not found in extra dir!", context.getName (), name);
                }
            } else if (logger.isTraceEnabled ()) {
                logger.trace ("[{}] the dir: {} not exists", context.getName (), extra);
            }
        }

        if (res == null) {
            // 在扩展配置目录下未找到相关配置文件，查找工作目录下的 conf 目录下的同名配置文件
            Path module_work_dir = context.getWorkdir ();
            if (module_work_dir != null && Files.exists (module_work_dir)) {
                Path conf = Paths.get (module_work_dir.toString (), name);
                if (Files.exists (conf)) {
                    res = conf.toUri ().toURL ();
                } else if (logger.isTraceEnabled()) {
                    logger.trace("[{}] resource file [{}] not found in module dir[{}]!", context.getName (), name, module_work_dir);
                }
            } else if (logger.isTraceEnabled ()) {
                logger.trace ("[{}] module_work_dir: {} not exists", context.getName (), module_work_dir);
            }
        }

        if (res == null) {
            // 在模块工作目录下也未找到配置文件，查找类路径下的同名配置文件
            // 搜索路径： /META-INF/，/，模块实现类的同级目录
            res = loader.getResource ("META-INF/" + name);
            if (res == null) {
                res = loader.getResource (name);
            }
            if (res == null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("[{}] resource file [{}] not found in classpath://META-INF/!", context.getName (), name);
                }

                String pathName = FileInfo.getFolder (className.replace ('.', '/'));
                res = loader.getResource (pathName + '/' + name);
            }
        }

        if (logger.isTraceEnabled ()) {
            logger.trace ("[{}] res = {}", context.getName (), res);
        }

        if (res != null && !searchExtra) {
            // 资源找到了，且 不搜索 扩展目录，意味着是外部模块
            // 排除从 classpath 加载的资源
            ClassLoader cl = getClass ().getClassLoader ();
            boolean is_system_classpath = false;
            while (cl != null) {
                if (cl instanceof URLClassLoader) {
                    URL[] resources = ((URLClassLoader) cl).getURLs ();
                    String path = res.toExternalForm ();
                    for (URL url : resources) {
                        if (path.startsWith (url.toExternalForm ())) {
                            // 在系统的 classpath 中存在，剔除
                            is_system_classpath = true;
                            break;
                        }
                    }
                }
                if (is_system_classpath) {
                    res = null;
                    break;
                }
                cl = cl.getParent ();
            }
        } else if (logger.isTraceEnabled()) {
            logger.trace("[{}] resource file [{}] not found in anywhere!", context.getName (), name);
        }

        return res;
    }

    /**
     * 查找模块的配置信息
     * <ol>
     *     <li>首先检查 extra 下是否存在同名配置文件</li>
     *     <li>检查模块工作目录下的 conf 目录下是否存在同名的配置配置文件</li>
     *     <li>搜索类路径下的 /META-INF/ 是否存在同名配置文件</li>
     *     <li>搜索类路径下的 / 是否存在同名配置文件</li>
     *     <li>搜索类路径下，模块实现类所在包下是否存在同名配置文件</li>
     * </ol>
     * @param name       资源名称
     * @param context    模块上下文环境
     * @param loader     模块的 classloader
     * @param className  模块的实现名
     * @throws IOException io exception
     */
    private URL findResource (String name, ModuleContextImpl context, ClassLoader loader, String className) throws IOException {
        boolean debug =
                debuggingModule != null && debuggingModule.name.equals (context.getName ());
        return findResource (name, context, loader, className, true, debug);
    }

    /**
     * 查找模块的配置信息
     * <ol>
     *     <li>首先检查 extra 下是否存在同名配置文件</li>
     *     <li>检查模块工作目录下的 conf 目录下是否存在同名的配置配置文件</li>
     *     <li>搜索类路径下的 /META-INF/ 是否存在同名配置文件</li>
     *     <li>搜索类路径下的 / 是否存在同名配置文件</li>
     *     <li>搜索类路径下，模块实现类所在包下是否存在同名配置文件</li>
     * </ol>
     * @param moduleName 模块名称
     * @param context    模块上下文环境
     * @param loader     模块的 classloader
     * @param className  模块的实现名
     * @throws IOException io exception
     */
    private void findConfig (String moduleName, ModuleContextImpl context, ClassLoader loader, String className) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace ("trying to find configuration file for module: {}", moduleName);
        }
        String configName = moduleName + ".conf";
        if (logger.isTraceEnabled()) {
            logger.trace ("extra = {}", extra_dir);
            logger.trace ("configName = {}", configName);
        }

        URL res = findResource (configName, context, loader, className);

        if (res != null) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("found module configuration at : {}", res.toExternalForm ());
            }
            Properties props = new Properties ();
            try (InputStream in = res.openStream ()) {
                props.load (in);
            }
            if (logger.isTraceEnabled ()) {
                Helper.prettyPrint (props, logger);
            }

            context.setConfiguration (new PropertyConfiguration (props));
        } else if (logger.isTraceEnabled ()) {
            logger.trace ("can't find config for module: {}", moduleName);
        }
    }

    private void loadExportedServices () {
        List<URL> urls = new ArrayList<> ();
        try (Stream<Path> jars = Files.list (Paths.get (extServices_dir)).filter (filter)) {
            jars.forEach (f -> {
                try {
                    urls.add (f.toUri ().toURL ());
                } catch (MalformedURLException ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            });
        } catch (IOException ex) {
            logger.warn (ex.getMessage (), ex);
        }

        serviceClassLoader = new ServiceClassLoader (urls.toArray(new URL[0]));
    }

    /**
     * 为框架启动准备环境
     * @throws IOException io exception
     */
    private void prepare () throws Exception {
        // 重写系统临时目录的路径到 ../tmp
        Path tmp = Paths.get (temp_dir);
        if (!Files.exists (tmp)) {
            Files.createDirectories (tmp);
        }
        System.setProperty ("java.io.tmpdir", tmp.toRealPath ().toString ());

        // 检查是否启动 JMX
        jmxEnabled = configuration.getBoolean ("integration.jmx.enabled", false);
        if (logger.isTraceEnabled ()) {
            logger.trace ("jmx enabled: {}", jmxEnabled);
        }
        if (jmxEnabled) {
            initJMX ();
        }

        if (logger.isTraceEnabled ()) {
            String module_dir = null;
            if (module_dirs != null) {
                StringBuilder builder = new StringBuilder ();
                for (String md : module_dirs) {
                    Path path = Paths.get (md);
                    if (Files.exists (path)) {
//                    if (Files.isDirectory (path)) {
                        if (builder.length () > 0) {
                            builder.append (File.pathSeparator);
                        }
                        builder.append (path.toRealPath ());
                    }
                }

                module_dir = builder.toString ();
            }

            logger.trace ("framework.tmp.dir      = {}", tmp.toRealPath ());
            logger.trace ("framework.modules.dir  = {}", module_dir);
            logger.trace ("ext.conf.dir           = {}", Paths.get (extra_dir).toRealPath ());
            logger.trace ("jmx.enabled            = {}", jmxEnabled);
        }
    }

    /**
     * 查找所有已定义的内置模块
     * @return 已定义的内置模块
     * @throws IOException io exception
     */
    private Map<String, ModuleInfo> mapBuildInModules () throws IOException {
        // 获取所有内置模块的配置信息
        ClassLoader loader = getClass ().getClassLoader ();
        Map<String, ModuleInfo> map = new HashMap<> ();

        for (Map.Entry<String, String> e : BUILD_IN_MODULES.entrySet ()) {
            ModuleInfo mi = new ModuleInfo (e.getKey (), e.getValue (), true);
            mi.version = VERSION;
            mi.memo    = MODULE_MEMO.get (e.getKey ());
            map.put (mi.name, mi);
        }

        map.putAll (mapModules (loader, true));

        if (logger.isTraceEnabled ()) {
            logger.trace ("loaded build-in modules: ");
            for (Map.Entry<String, ModuleInfo> e : map.entrySet ()) {
                logger.trace ("\t{} -> {}", e.getKey (), e.getValue ());
            }
        }
        return map;
    }

    private Map<String, ModuleInfo> mapModules (ClassLoader loader, boolean buildIn) throws IOException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("mapping modules with classloader: {}, it is build-in module? : {}", loader, buildIn);
        }
        // 获取所有内置模块的配置信息
        Map<String, ModuleInfo> map = new HashMap<> ();

        if (buildIn) {
            for (Enumeration<URL> e = loader.getResources ("META-INF/module.properties"); e.hasMoreElements (); ) {
                URL url = e.nextElement ();
                try (InputStream in = url.openStream ()) {
                    ModuleInfo info = Helper.findModuleInfo (in, true);
                    if (info != null) {
                        map.put (info.name, info);
                    }
                }
            }
        } else {
            if (loader instanceof ModuleClassLoader) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("load module info as module classloader.");
                }
                try {
                    ModuleInfo info = ((ModuleClassLoader) loader).getModuleInfo ();
                    if (info != null) {
                        map.put (info.name, info);
                    }
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            } else {
                try (InputStream in = loader.getResourceAsStream ("META-INF/module.properties")) {
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("loading module info from META-INF/module.properties, and in = {}", in);
                    }
                    ModuleInfo info = Helper.findModuleInfo (in, false);
                    if (info != null) {
                        map.put (info.name, info);
                    }
                }
            }
        }

        return map;
    }

    /**
     * 展开模块的依赖列表
     * @param map 所有模块
     * @throws IntegrationException 若有依赖模块未找到，抛出错误
     */
    private void extract (Map<String, ModuleInfo> map) throws IntegrationException {
//        Map<String, ModuleInfo> copy = Collections.synchronizedMap (new HashMap<> (map));
        Map<String, ModuleInfo> copy = new HashMap<> (map);
        for (ModuleInfo info : copy.values ()) {
            if (!StringUtil.isEmpty (info.extra)) {
                String[] tmp = info.extra.trim ().split ("[\\s,]");
                for (String name : tmp) {
                    if (!StringUtil.isEmpty (name)) {
                        name = name.trim ();
                        if (copy.containsKey (name)) {
                            // 框架启动时
                            info.dependencies.put (name, copy.get (name));
                        } else if (buildIns != null && buildIns.containsKey (name)) {
                            if (logger.isTraceEnabled ()) {
                                logger.trace ("[{}] module is the build-in module!", info.name);
                            }
//                            continue;
                        } else if (contexts.containsKey (name)) {
                            // 框架运行时
                            ModuleInfo mi = contexts.get (name).getInfo ();
                            info.dependencies.put (name, mi);
                            if (!loadedModules.containsKey (name)) {
                                loadedModules.put (name, mi);
                            }
                        } else if (buildIns !=null && !buildIns.containsKey (name)) {
                            throw new IntegrationException ("depended module [" + name + "] not found");
                        }
                    }
                }
            }
        }
    }

    /**
     * 加载外部模块
     * @throws IntegrationException 依赖缺失时抛出
     * @throws IOException io exception
     */
    private void loadExtModules () throws IntegrationException, IOException {
        // 加载已部署的模块
        if (null != module_dirs && module_dirs.length > 0) {
            Map<String, ModuleClassLoader> loaders = new HashMap<> ();
            Map<String, Path> dirs = new HashMap<> ();

            for (String module : module_dirs) {
                Path dir = Paths.get (module);
                if (Files.exists (dir)) {
//                if (Files.isDirectory (dir)) {
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("scanning directory: {}", dir);
                    }

                    try (Stream<Path> temps = Files.list (dir).filter (Files::isDirectory)) {
                        temps.forEach (p -> {
                            logger.trace ("p = {}", p);
                            List<URL> urls = new ArrayList<> ();
                            try (Stream<Path> jars = Files.list (Paths.get (p.toString (), "libs")).filter (filter)) {
                                jars.forEach (f -> {
                                    try {
                                        urls.add (f.toUri ().toURL ());
                                    } catch (MalformedURLException ex) {
                                        logger.warn (ex.getMessage (), ex);
                                    }
                                });

                                if (!urls.isEmpty ()) {
                                    String name = p.getFileName ().toString ();
                                    ModuleClassLoader loader = new ModuleClassLoader (name, urls.toArray (new URL[0]), serviceClassLoader);
                                    loaders.put (name, loader);
                                    dirs.put (name, p);
                                }
                            } catch (IOException ex) {
                                logger.warn (ex.getMessage (), ex);
                            }
                        });
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                        throw new IntegrationException (ex);
                    }
                }
            }

            if (logger.isTraceEnabled ()) {
                logger.trace ("loaders = {}", loaders);
            }
            if (!loaders.isEmpty ()) {
                loadedModules = new HashMap<> (loaders.size ());
                try {
                    for (ModuleClassLoader loader : loaders.values ()) {
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("mapping module: {} ...", loader);
                        }
                        loadedModules.putAll (mapModules (loader, false));
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("module: {} mapped.", loader);
                        }
                    }
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("mapped modules: {}", loadedModules);
                    }

                    if (!loadedModules.isEmpty ()) {
                        extract (loadedModules);

                        List<ModuleInfo> orderedModules = Helper.order (loadedModules);
                        if (!orderedModules.isEmpty ()) {
                            if (logger.isTraceEnabled ()) {
                                logger.trace ("ordered modules: {}", orderedModules);
                            }

                            for (ModuleInfo info : orderedModules) {
                                if (!loaders.containsKey (info.name)) {
                                    logger.warn ("classloader for module [{}] not found. ignore this module!", info.name);
                                } else {
                                    try {
                                        startModule (info, loaders.get (info.name), dirs.get (info.name), false);
                                        allModules.put (info.name, info);
                                    } catch (IntegrationException error) {
                                        logger.error ("cannot start module [{}], because of: ", info.name);
                                        logger.error (error.getMessage (), error);
                                    }
                                }
                            }
                            if (logger.isTraceEnabled ()) {
                                logger.trace ("the loaded modules are: ");
                                allModules.forEach ((key, info) -> logger.trace ("{} -> {}", key, info));
                            }
//                            allModules.putAll (loadedModules);
                        }
                    }
                } finally {
                    loaders.clear ();
                }
            }
        }
    }

    /**
     * 查找合适的数据库配置，并注册
     * @param context   模块容器上下文
     * @param className 模块启动类
     * @throws IOException io exception
     */
    private void configDatabase (ModuleContextImpl context, String moduleName, String className, boolean searchExtra, boolean debug) throws IOException, IntegrationException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to find database config for module: {} ...", moduleName);
        }
        DataSourceService service = (DataSourceService) router.findService (IDatabaseService.class);
        if (service == null) {
            logger.warn ("database service is not present. ignore this request");

            return;
        }

        // 创建数据库连接服务
        ClassLoader loader = context.getContextClassLoader ();
        URL url = null;
        for (String resName : data_config_names) {
            url = findResource (resName, context, loader, className, searchExtra, debug);
            if (url != null) {
                break;
            }
        }
        if (url != null) {
            String path   = url.toExternalForm ();
            String format = FileInfo.getExtension (path);

            if (logger.isTraceEnabled ()) {
                logger.trace ("found database config as {}", path);
            }

            if ("properties".equals (format) || "conf".equals (format)) {
                // properties file
                try (InputStream in = url.openStream ()) {
                    String configName = Helper.loadJDBCConfig (in, service);
                    if (!StringUtil.isEmpty (configName)) {
                        context.addDatabaseConfig (configName);
                    }
                }
            } else {
                // xml file
                try (InputStream in = url.openStream ()) {
                    try {
                        Map<String, Properties> map = Helper.translateXML (in);
                        if (!map.isEmpty ()) {
                            for (Map.Entry<String, Properties> e : map.entrySet ()) {
                                String configName = e.getKey ();
                                Properties props = e.getValue ();

                                if (service.getDataSource (configName) != null) {
                                    throw new IntegrationException ("database config [" + configName + "] already exist!");
                                }
                                try {
                                    if (logger.isTraceEnabled ()) {
                                        Helper.prettyPrint (props, logger);
                                    }

                                    service.register (configName, props);
                                    context.addDatabaseConfig (configName);
                                } catch (Exception ex) {
                                    throw new IntegrationException (ex);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage ());
                    }
                }
            }
        } else if (logger.isTraceEnabled ()) {
            logger.trace ("no database config defined for module [{}]", moduleName);
        }
    }

    private static final Pattern MQTT_PATTERN = Pattern.compile ("^mqtt\\.(.*?)\\.url$");
    private void configMqtt (ModuleContextImpl context, String moduleName) throws IOException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to config mqtt for module: {}", moduleName);
        }

        IMqttService service = router.findService (IMqttService.class);
        if (service == null) {
            logger.warn ("mqtt module is not present, ignore this request!");
            return;
        }

        ClassLoader loader = context.getContextClassLoader ();
        Properties props = new Properties ();
        try (InputStream in = loader.getResourceAsStream ("META-INF/mqtt.conf")) {
            if (in != null) {
                props.load (in);
            }
        }

        if (!props.isEmpty ()) {
            Set<String> config_names = new HashSet<> ();
            for (String key : props.stringPropertyNames ()) {
                Matcher m = MQTT_PATTERN.matcher (key);
                if (m.matches ()) {
                    String name = m.group (1);
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("found a mqtt config name: {}", name);
                    }
                    config_names.add (name);
                }
            }

            if (!config_names.isEmpty ()) {
                Set<String> delete = new HashSet<> ();
                for (String name : service.getAllNames ()) {
                    if (config_names.contains (name)) {
                        logger.warn ("mqtt config: {} already exists", name);
                    }
                    delete.add (name);
                }

                if (!delete.isEmpty ()) {
                    config_names.removeAll (delete);
                }
                delete.clear ();

                if (!config_names.isEmpty ()) {
                    for (String name : config_names) {
                        Properties conf = new Properties ();
                        String url      = props.getProperty ("mqtt." + name + ".url");
                        String user     = props.getProperty ("mqtt." + name + ".user");
                        String password = props.getProperty ("mqtt." + name + ".password");
                        String clientId = props.getProperty ("mqtt." + name + ".client_id");

                        conf.setProperty ("url", url);
                        if (!StringUtil.isEmpty (user)) {
                            conf.setProperty ("userName", user);
                        }
                        if (!StringUtil.isEmpty (password)) {
                            conf.setProperty ("password", password);
                        }
                        if (!StringUtil.isEmpty (clientId)) {
                            conf.setProperty ("clientId", clientId);
                        }

                        if (logger.isTraceEnabled ()) {
                            Helper.prettyPrint (conf, logger);

                            logger.trace ("registering mqtt config: {}", name);
                        }
                        service.register (name, conf);
                        context.addMqttConfig (name);
                    }
                }
            }
        }
    }

    private void listenShutdownService () throws IOException {
        int port = (int) (Math.random () * 100);
        port += 65000;
        logger.info ("shutdown service listen on udp:{}", port);
        // 起一个 udp 端口来监听 shutdown 请求
        socket = new DatagramSocket (port, InetAddress.getByName ("127.0.0.1"));
        executor.execute (() -> {
            try {
                byte[] buff = new byte[8];  // GoodBye!
                DatagramPacket packet = new DatagramPacket (buff, buff.length);
                socket.receive (packet);
                if (logger.isTraceEnabled ()) {
                    logger.trace ("received data: {}", StringUtil.format (buff));
                }
                String text = new String (buff);
                if ("GoodBye!".equals (text)) {
                    try {
                        shutdown ();
                    } catch (Exception ignore) {}
                }
            } catch (IOException ex) {
                logger.warn (ex.getMessage (), ex);
            }
        });
        // 将随机出来的端口号写到文件中
        Path path = Paths.get (this.temp_dir, PORT_FILE_NAME);
        Files.write (path, String.valueOf (port).getBytes(StandardCharsets.UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);
        if (logger.isTraceEnabled ()) {
            logger.trace ("the shutdown port number has been wrote in file: {}", path.toRealPath ());
        }
    }

    private static final class ModuleStartupTask implements StartupMonitor.ITask {
        IModule module;
        IModuleContext context;

        final Logger logger = LoggerFactory.getLogger (ModuleStartupTask.class);

        private ModuleStartupTask (IModule module, IModuleContext context) {
            this.module  = module;
            this.context = context;
        }

        @Override
        public void start () {
            if (module != null) try {
                // 自动装配依赖注入
                autoWare ((ModuleContextImpl) context, module);
                module.startup (context);
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
//                throw new RuntimeException (ex);
                stop ();
            }
        }

        @Override
        public void stop () {
            if (module != null) try {
                module.destroy ();
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
        }
    }

    private static final class Wrapper {
        String name;
        int count;

        Wrapper (String name, int count) {
            this.name  = name;
            this.count = count;
        }

        @Override
        public String toString () {
            return "{" + name + ":" + count + "}";
        }
    }
}