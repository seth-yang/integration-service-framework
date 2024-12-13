package org.dreamwork.integration.bootloader;

import org.dreamwork.integration.context.IntegrationFramework;
import org.apache.log4j.PropertyConfigurator;
import org.dreamwork.cli.ArgumentParser;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.dreamwork.integration.util.Helper.prettyPrint;

public class Bootloader {
    public static void main (String[] args) throws Exception {
        ClassLoader loader = Bootloader.class.getClassLoader ();
        ArgumentParser parser = null;
        try (InputStream in = loader.getResourceAsStream ("integration.json")) {
            if (in != null) {
                String content = new String (IOUtil.read (in), StandardCharsets.UTF_8);
                parser = new ArgumentParser (content);
                parser.parse (args);
            }
        }

        if (parser == null) {
            System.err.println ("can't initial command line parser");
            System.exit (-1);
        }

        initLogger (loader, parser);

        Logger logger = LoggerFactory.getLogger (Bootloader.class);
        logger.info ("starting integration framework ...");
        Properties props = parseConfig (parser, logger);
        PropertyConfiguration configuration = new PropertyConfiguration (props);
        // patch extra config dir
        setDefaultValue (parser, configuration, "ext.conf.dir", 'e');
        // patch jmx enable settings
        setDefaultValue (parser, configuration, "jmx.enabled", 'X');

        startBootloader(configuration,logger);

    }

    public static void startBootloader(IConfiguration configuration, Logger logger) throws Exception {
        boolean shutdown = configuration.getBoolean ("framework.action.shutdown", false);
        if (shutdown) {
            shutdown (configuration, logger);
        } else {
            boolean showVersion = configuration.getBoolean ("framework.action.show-version", false);
            if (showVersion) {
                System.out.printf ("Hothink Integration Framework v:%s%n", IntegrationFramework.VERSION);
            } else {
                IntegrationFramework framework = new IntegrationFramework ((PropertyConfiguration) configuration);
                framework.startup ();
            }
        }
    }

    private static void setDefaultValue (ArgumentParser parser, PropertyConfiguration configuration, String key, char argument) {
        if (parser.isArgPresent (argument)) {
            configuration.setRawProperty (key, parser.getValue (argument));
        }
        if (!configuration.contains (key)) {
            configuration.setRawProperty (key, parser.getDefaultValue (argument));
        }
    }

    private Bootloader () {}

    private static void initLogger (ClassLoader loader, ArgumentParser parser) throws IOException {
        String logLevel, logFile;
        if (parser.isArgPresent ('v')) {
            logLevel = "TRACE";
        } else if (parser.isArgPresent ("log-level")) {
            logLevel = parser.getValue ("log-level");
        } else {
            logLevel = parser.getDefaultValue ("log-level");
        }

        logFile = parser.getValue ("log-file");
        if (StringUtil.isEmpty (logFile)) {
            logFile = parser.getDefaultValue ("log-file");
        }
        File file = new File (logFile);
        File parent = file.getParentFile ();
        if (!parent.exists () && !parent.mkdirs ()) {
            throw new IOException ("Can't create dir: " + parent.getCanonicalPath ());
        }

        try (InputStream in = loader.getResourceAsStream ("internal-log4j.properties")) {
            Properties props = new Properties ();
            props.load (in);

            System.out.println ("### setting log level to " + logLevel + " ###");
            if ("trace".equalsIgnoreCase (logLevel)) {
                props.setProperty ("log4j.rootLogger", "INFO, stdout, FILE");
                props.setProperty ("log4j.appender.FILE.File", logFile);
                props.setProperty ("log4j.appender.FILE.Threshold", logLevel);
                props.setProperty ("log4j.logger.org.dreamwork", "trace");
                props.setProperty ("log4j.logger.org.dreamwork", "trace");
            } else {
                props.setProperty ("log4j.rootLogger", logLevel + ", stdout, FILE");
                props.setProperty ("log4j.appender.FILE.File", logFile);
                props.setProperty ("log4j.appender.FILE.Threshold", logLevel);
            }

            PropertyConfigurator.configure (props);
        }
    }

    private static Properties parseConfig (ArgumentParser parser, Logger logger) throws IOException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("parsing config file ...");
        }
        String config_file = null;
        if (parser.isArgPresent ('c')) {
            config_file = parser.getValue ('c');
        }
        if (StringUtil.isEmpty (config_file)) {
            config_file = parser.getDefaultValue ('c');
        }

        config_file = config_file.trim ();
        if (logger.isTraceEnabled ()) {
            logger.trace ("config file: {}", config_file);
        }
        File file;
        if (config_file.startsWith ("file:/") || config_file.startsWith ("/")) {
            file = new File (config_file);
        } else {
            file = new File (".", config_file);
        }

        if (!file.exists ()) {
            System.err.println ("can't find config file: " + config_file);
            System.exit (-1);
        }

        Properties props = new Properties ();
        try (InputStream in = Files.newInputStream (file.toPath ())) {
            props.load (in);
        }

        if (logger.isTraceEnabled ()) {
            prettyPrint (props, logger);
        }
        return props;
    }

    private static void shutdown (IConfiguration configuration, Logger logger) throws IOException {
        String dir     = configuration.getString ("framework.tmp.dir");
        Path file      = Paths.get (dir, IntegrationFramework.PORT_FILE_NAME);
        byte[] data    = Files.readAllBytes (file);
        String text    = new String (data);
        int port       = Integer.parseInt (text);
        byte[] goodbye = "GoodBye!".getBytes ();
        if (logger.isTraceEnabled ()) {
            logger.trace ("got shutdown port: {}", port);
        }
        InetAddress localhost = InetAddress.getByName ("127.0.0.1");
        DatagramPacket packet = new DatagramPacket (goodbye, 8, localhost, port);
        try (DatagramSocket socket = new DatagramSocket ()) {
            socket.send (packet);
        }
        Files.deleteIfExists (file);
    }
}