package org.dreamwork.integration.util;

import org.dreamwork.integration.services.IFrameworkService;
import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by seth.yang on 2020/4/22
 */
public class ModuleDeployer {
    private final static Logger logger = LoggerFactory.getLogger (ModuleDeployer.class);

    private final IFrameworkService service;

    public ModuleDeployer (IFrameworkService service) {
        this.service = service;
    }

    /**
     * 发布一个模块
     * @param src    一个包含模块相关配置的 jar 或者 zip
     * @param target 模块的发布目录
     * @return 如果发布成功，返回<code>模块目录</code>，否则返回<code>null</code>
     * @throws IOException io exception
     */
    public String deploy (File src, File target) throws IOException  {
        if (src.isFile () && src.canRead ()) {
            String name = src.getName ();
            if (name.endsWith (".jar")) {
                checkTarget (target);

                return deploySingleJar (src, target);
            } else if (name.endsWith (".zip")) {
                checkTarget (target);
                return deployZip (src, target);
            } else {
                throw new IOException ("Unknown module format: " + src);
            }
        } else {
            String message = String.format ("%s is not a regular file or cannot read.", src);
            throw new IOException (message);
        }
    }

    private void checkModule (String moduleName) throws IOException {
        if (service.getContextNames ().contains (moduleName)) {
            String message = String.format ("module [%s] is running, stop it first.", moduleName);
            logger.warn (message);
            throw new IOException (message);
        }
    }

    /**
     * 发布一个单jar的模块
     * @param src    jar 的物理位置
     * @param target 发布目录
     * @return 如果发布成功，返回<code>模块名称</code>，否则返回<code>null</code>
     * @throws IOException io exception
     */
    private String deploySingleJar (File src, File target) throws IOException {
        String moduleName = findModuleName (src);

        if (!StringUtil.isEmpty (moduleName)) {
            checkModule (moduleName);

            File libs = new File (target, moduleName + "/libs");
            Files.createDirectories(libs.toPath());
/*
            if (!libs.exists () && !libs.mkdirs ()) {
                throw new IOException("cannot create directory " + libs.toString());
            }
*/

            Files.copy(src.toPath(), Paths.get(libs.toPath().toString(), src.getName()));

/*
            try (InputStream in = new FileInputStream (src)) {
                File dst = new File (libs, src.getName ());
                try (OutputStream out = new FileOutputStream (dst)) {
                    IOUtil.dump (in, out);
                    out.flush ();
                }
            }
*/
            return moduleName;
        }
        return null;
    }

    /**
     * 发布一个zip打包的模块
     * @param src    zip 物理位置
     * @param target 发布的目标目录
     * @return 如果发布成功，返回<code>模块名称</code>，否则返回<code>null</code>
     * @throws IOException io exception
     */
    private String deployZip (File src, File target) throws IOException {
        String uuid = StringUtil.uuid();
        File tmp = new File (System.getProperty ("java.io.tmpdir"), uuid);
        try {
            // 解压到临时目录
            if (!tmp.mkdir ()) {
                logger.warn ("cannot create directory: {}", tmp);
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("tmp dir: {} created. trying to unzip content into it.", tmp);
            }
            unzip (src, tmp);

            File lib = new File (tmp, "libs");
            File[] files = lib.listFiles (p -> p.getName ().endsWith (".jar"));

            String moduleName = null;
            if (files != null) {
                for (File p : files) {
                    if ((moduleName = findModuleName (p)) != null) {
                        break;
                    }
                }

                if (logger.isTraceEnabled ()) {
                    logger.trace ("found module name = {}", moduleName);
                }

                if (!StringUtil.isEmpty (moduleName)) {

                    checkModule (moduleName);

                    File dir = new File (target.toString (), moduleName);
                    if (dir.isDirectory ()) {
                        // 目录已经存在，先清空目录内容
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("the dir: {} already exists, clear it's contents", dir);
                        }
                        delete (dir.toPath ());
                    } else if (!dir.exists () && !dir.mkdirs ()) {
                        throw new IOException ("cannot create directory: " + dir);
                    }
                    unzip (src, dir);
                    return moduleName;
                } else {
                    String message = "cannot find module name. it is not a valid module!";
                    logger.warn (message);
                    throw new IOException (message);
                }
            } else {
                String message = "no jar files found. it is not a valid module!";
                logger.warn (message);
                throw new IOException (message);
            }
        } finally {
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying to clean temp resources ");
            }
            if (tmp.exists ()) {
                delete (tmp.toPath ());
            }
        }
    }

    private static void checkTarget (File target) throws IOException {
        if (!target.exists ()) {
            Files.createDirectories(target.toPath());

            if (logger.isTraceEnabled ()) {
                logger.trace ("directory {} created.", target);
            }
        } else if (!target.isDirectory ()) {
            throw new IOException (target.getCanonicalPath () + " already exist and it is not a directory");
        }
    }

    public static void unzip (File zip, File outPath) throws IOException {
        if (zip.exists ()) {
            try (ZipFile zipFile = new ZipFile (zip)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries ();
                while (entries.hasMoreElements ()) {
                    ZipEntry entry = entries.nextElement ();
                    if (entry.isDirectory ()) {
                        File dir = new File (outPath, entry.getName ());
                        if (!dir.mkdirs ()) {
                            logger.warn ("cannot create dir: {}", dir);
                        }
                    } else {
                        File file = new File (outPath, entry.getName ());
                        File parent = file.getParentFile ();
                        if (!parent.exists () && !parent.mkdirs ()) {
                            throw new IOException ("can't create directory: " + parent.getCanonicalPath ());
                        }
                        try (OutputStream out = new FileOutputStream (file)) {
                            try (InputStream in = zipFile.getInputStream (entry)) {
                                IOUtil.dump (in, out);
                                out.flush ();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void delete (Path target) throws IOException {
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                boolean flag = Files.deleteIfExists(dir);
                if (logger.isTraceEnabled()) {
                    if (flag) {
                        logger.trace ("dir: [{}] delete success!", dir);
                    } else {
                        logger.trace ("dir: [{}] cannot be deleted.", dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String findModuleName (File src) throws IOException {
        String moduleName = null;
        try (JarFile jar = new JarFile (src)) {
            JarEntry entry = jar.getJarEntry("META-INF/module.properties");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    Properties props = new Properties ();
                    props.load (in);

                    moduleName = props.getProperty ("module.name");
                }
            }
        }
        return moduleName;
    }
}