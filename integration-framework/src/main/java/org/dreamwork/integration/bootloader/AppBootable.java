package org.dreamwork.integration.bootloader;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.app.bootloader.IBootable;
import org.dreamwork.config.IConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dreamwork.integration.bootloader.Bootloader.startBootloader;

@IBootable(argumentDef = "integration.json")
public class AppBootable {
    private final Logger logger = LoggerFactory.getLogger (AppBootable.class);

    public static void main (String[] args) throws Exception {
        ApplicationBootloader.run(AppBootable.class,args);
    }
    public void start (IConfiguration configuration) throws Exception {
        startBootloader(configuration,logger);
    }
}

