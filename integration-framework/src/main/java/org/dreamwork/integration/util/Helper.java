package org.dreamwork.integration.util;

import com.google.gson.Gson;
import org.dreamwork.integration.api.ConfigurationNotFoundException;
import org.dreamwork.integration.api.IntegrationException;
import org.dreamwork.integration.api.ModuleInfo;
import org.dreamwork.integration.api.annotation.AConfigured;
import org.dreamwork.integration.api.services.IDatabaseService;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.misc.XMLUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Helper {
    private static final Logger logger = LoggerFactory.getLogger (Helper.class);

    public static void prettyPrint (Properties props, Logger logger) {
        logger.trace ("### global configuration ###");
        int length = 0;
        List<String> list = new ArrayList<> ();
        for (String key : props.stringPropertyNames ()) {
            list.add (key);
            if (key.length () > length) {
                length = key.length ();
            }
        }
        list.sort (String::compareTo);
        for (String key : list) {
            StringBuilder builder = new StringBuilder (key);
            if (key.length () < length) {
                int d = length - key.length ();
                for (int i = 0; i < d; i ++) {
                    builder.append (' ');
                }
            }
            builder.append (" : ").append (props.getProperty (key));
            logger.trace (builder.toString ());
        }
        logger.trace ("############################");
    }

/*

*/

    /**
     * 根据依赖关系排序
     * @param map 所有集合
     * @return 排好序的列表
     */
    public static List<ModuleInfo> order (Map<String, ModuleInfo> map) {
        List<ModuleInfo> ordered = new ArrayList<> (map.size ());
        for (ModuleInfo info : map.values ()) {
            if (info.dependencies.isEmpty ()) {
                ordered.add (info);
            }
        }

        order (map, ordered);

        return ordered;
    }

    private static void order (Map<String, ModuleInfo> map, List<ModuleInfo> ordered) {
        for (ModuleInfo info : map.values ()) {
            if (!ordered.contains (info)) {
                if (!info.dependencies.isEmpty ()) {
                    order (info.dependencies, ordered);
                }
                ordered.add (info);
            }
        }
    }

    /**
     * 查找所有依赖指定名称的列表
     * @param name    指定的元素
     * @param ordered 经过 {@link #order(Map)} 排序的列表
     * @return 找到的，需要受到影响的元素的列表
     */
    public static Set<ModuleInfo> findDependencies (String name, List<ModuleInfo> ordered) {
        Set<ModuleInfo> set = new HashSet<> ();
        for (int i = 0; i < ordered.size (); i ++) {
            if (ordered.get (i).name.equals (name)) {
                // 1. 加入需要排除的
                set.add (ordered.get (i));
                int pos = i;    // 保存直接排除的位置
                while (++i < ordered.size ()) {     // 只有在右边的元素才可能依赖这个元素
                    ModuleInfo info = ordered.get (i);
                    if (!info.dependencies.isEmpty ()) {
                        for (ModuleInfo mi : info.dependencies.values ()) {
                            if (name.equals (mi.name)) {
                                // 被依赖的元素的名称直接命中，排除掉
                                set.add (info);
                            } else {
                                int index = ordered.indexOf (mi);
                                // 只有右边的元素才可能依赖这个元素，
                                // 且 配出列表中有这个依赖元素
                                if (index > pos && set.contains (mi)) {
                                    // 这个元素也应该被排除
                                    set.add (info);
                                }
                            }
                        }
                    }
                }
            }
        }
        return set;
    }

    /*
    <?xml version="1.0" encoding="UTF-8"?>
<Context path="/">
    <Resource name="jdbc/cng" auth="Container" type="javax.sql.DataSource"
              maxActive="30" maxIdle="10" maxWait="10000"
              logAbandoned="true" removeAbandoned="true" removeAbandonedTimeout="60"
              username="cng" password="cng" driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://192.168.2.29/cng"/>
</Context>
     */
    public static Map<String, Properties> translateXML (InputStream in) throws IOException, SAXException, ParserConfigurationException {
        Document doc = XMLUtil.parse (in);
        NodeList list = doc.getElementsByTagName ("Resource");

        Map<String, Properties> map = new HashMap<> ();
        if (list != null && list.getLength () > 0) {
            for (int i = 0; i < list.getLength (); i ++) {
                Element res = (Element) list.item (i);
                String name = res.getAttribute ("name");
                if (StringUtil.isEmpty (name)) {
                    logger.warn ("no database config name present, ignore this element");

                    continue;
                }

                NamedNodeMap attrs = res.getAttributes ();
                if (attrs != null && attrs.getLength () > 0) {
                    Properties props = new Properties ();
                    for (int j = 0; j < attrs.getLength (); j ++) {
                        Node attr = attrs.item (j);
                        String attrName = attr.getNodeName ();
                        props.setProperty (attrName, res.getAttribute (attrName));
                    }
                    map.put (name, props);
                }
            }
        }
        return map;
    }

    public static String loadJDBCConfig (InputStream in, IDatabaseService service) throws IntegrationException, IOException {
        Properties conf = new Properties ();
        conf.load (in);

        String configName = conf.getProperty ("db.pool.name");
        if (service.getDataSource (configName) != null) {
            throw new IntegrationException ("database config [" + configName + "] already exist!");
        }

        loadJDBCConfig (service, conf, configName);
        return configName;
    }

    public static void loadJDBCConfig (IDatabaseService service, Properties conf, String configName) throws IntegrationException {
        try {
            String host = conf.getProperty ("db.host");
            String dbName = conf.getProperty ("db.name");
            String port   = conf.getProperty ("db.port");

            Properties props = new Properties ();
            props.setProperty ("maxTotal", conf.getProperty ("db.pool.max.total"));
            props.setProperty ("maxIdle", conf.getProperty ("db.pool.max.idle"));
            props.setProperty ("maxWaitMillis", "10000");
            props.setProperty ("logAbandoned", "true");
            props.setProperty ("removeAbandonedTimeout", "60");
            props.setProperty ("username", conf.getProperty ("db.user.name"));
            props.setProperty ("password", conf.getProperty ("db.password"));
            props.setProperty ("driverClassName", "org.postgresql.Driver");
            props.setProperty ("url", "jdbc:postgresql://" + host + ':' + port + '/' + dbName);

            if (logger.isTraceEnabled ()) {
                Helper.prettyPrint (props, logger);
            }

            service.register (configName, props);
        } catch (Exception ex) {
            throw new IntegrationException (ex);
        }
    }

    public static ModuleInfo findModuleInfo (InputStream in, boolean internal) throws IOException {
        Properties props = new Properties ();
        props.load (in);

        String name = props.getProperty ("module.name");
        String impl = props.getProperty ("module.impl");
        String extr = props.getProperty ("dependency");
        String ctx  = props.getProperty ("context.path");
        String ver  = props.getProperty ("module.version");
        String memo = props.getProperty ("module.memo");

        // @since 1.1.0
        String base  = props.getProperty ("context.base");
        String httpd = props.getProperty ("require.httpd");
        String packages = props.getProperty ("api.packages");

        if (!StringUtil.isEmpty (name)) {
            ModuleInfo info = new ModuleInfo (name, impl, internal);
            if (!StringUtil.isEmpty (extr)) {
                info.extra = extr;
            }
            if (!StringUtil.isEmpty (ctx)) {
                info.context = ctx;
            }
            if (!StringUtil.isEmpty (ver)) {
                info.version = ver;
            }
            if (!StringUtil.isEmpty (memo)) {
                info.memo = memo;
            }
            // @since 1.1.0
            if (!StringUtil.isEmpty (base)) {
                info.basedir = base;
            }
            if (!StringUtil.isEmpty (httpd)) {
                try {
                    info.requireHttpd = Boolean.parseBoolean (httpd);
                } catch (Exception ignore) {}
            }
            if (!StringUtil.isEmpty (packages)) {
                if (!packages.contains (",")) {
                    info.apiPackages = new String[] {packages.trim ()};
                } else {
                    info.apiPackages = packages.trim ().split (",\\s*");
                }
            }
            return info;
        }

        return null;
    }

    public static ModuleInfo findModuleInfo (File src) throws IOException {
        try (JarFile jar = new JarFile (src)) {
            JarEntry entry = jar.getJarEntry("META-INF/module.properties");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    return findModuleInfo (in, false);
                }
            }
        }
        return null;
    }

    public static ModuleInfo copy (ModuleInfo module, Map<String, ModuleInfo> mapped) {
        final ModuleInfo copied = new ModuleInfo (module.name, module.impl, module.internal);
        if ("framework-manager".equals (module.name)) {
            copied.internal = true;
        }
        copied.context = module.context;
        copied.extra   = module.extra;
        copied.version = module.version;
        copied.api     = module.api;
        copied.memo    = module.memo;
        copied.running = module.running;

        if (!module.dependencies.isEmpty ()) {
            for (ModuleInfo info : module.dependencies.values()) {
                if (!mapped.containsKey (info.name)) {
                    ModuleInfo mi = copy (info, mapped);
                    mapped.put (mi.name, mi);
                }
            }
        }

        return copied;
    }

    /**
     * 注入配置
     * @param conf   全局配置对象
     * @param bean   对象实例
     * @param fields 需注入配置的所有字段
     * @throws IllegalAccessException 当访问字段异常时抛出
     */
    public static void configureFields (IConfiguration conf, Object bean, Collection<Field> fields) throws IllegalAccessException {
        Gson g = new Gson ();
        for (Field field : fields) {
            AConfigured ac = field.getAnnotation (AConfigured.class);
            String key = ac.value ();
            if (StringUtil.isEmpty (key)) {
                key = ac.key ();
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying inject field {} with key {}", field, key);
            }

            if (StringUtil.isEmpty (key)) {
                Class<?> type = field.getDeclaringClass ();
                key = "${" + type.getCanonicalName () + "." + field.getName () + "}";
            }
            String expression;
            if (key.startsWith ("${") && key.endsWith ("}")) {
                key = key.substring (2, key.length () - 1);
                expression = conf.getString (key);
            } else {
                expression = key.trim ();
            }
            if (expression != null) {
                Object value;
                Class<?> type = field.getType ();
                if (type.isAssignableFrom (String.class)) {
                    value = expression;
                } else {
                    try {
                        value = g.fromJson (expression, type);
                    } catch (Exception ex) {
                        logger.error ("cannot convert {} to {} when injecting {}", expression, type, field);
                        throw new RuntimeException (ex);
                    }
                }
                if (value != null) {
                    if (!field.isAccessible ()) {
                        field.setAccessible (true);
                    }

                    field.set (bean, value);
                }
            } else if (ac.required ()) {
                throw new ConfigurationNotFoundException ("configuration item [" + key + "] not found, but it is required");
            }
        }
    }

    public static void main (String[] args) {
        Map<String, ModuleInfo> map = new HashMap<> ();
        map.put ("a", new ModuleInfo ("a", "A", true));
        map.put ("b", new ModuleInfo ("b", "B", true));
        map.put ("c", new ModuleInfo ("c", "C", true));
        map.put ("d", new ModuleInfo ("d", "D", true));
        map.put ("e", new ModuleInfo ("e", "E", true));
        map.put ("f", new ModuleInfo ("f", "F", true));

        map.get ("a").addDependency (map.get ("b"));
        map.get ("a").addDependency (map.get ("c"));
        map.get ("a").addDependency (new ModuleInfo ("g", "G", true));
        map.get ("c").addDependency (map.get ("e"));
        map.get ("c").addDependency (map.get ("f"));
        map.get ("d").addDependency (map.get ("e"));
        map.get ("f").addDependency (map.get ("b"));

        List<ModuleInfo> ordered;
        System.out.println (map);
        System.out.println ("===============================");
        System.out.println (ordered = order (map));
        System.out.println ("===============================");
        System.out.println (findDependencies ("b", ordered));
    }
}
