package org.dreamwork.integration.services.impl;

import org.dreamwork.integration.api.services.ISystemService;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.db.IDatabase;
import org.dreamwork.db.SQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by seth.yang on 2020/4/21
 */
public class SystemServiceImpl implements ISystemService {
    private final SQLite sqlite;

    public SystemServiceImpl (IConfiguration conf) throws IOException {
        Path dir = Paths.get (conf.getString ("framework.database.dir", "../database/"));
        Logger logger = LoggerFactory.getLogger (SystemServiceImpl.class);
        if (logger.isTraceEnabled ()) {
            logger.trace ("find database config: {}", dir);
        }
        if (!Files.exists (dir) && Files.createDirectories (dir) == null) {
            throw new IOException ("Can't create directory: " + dir);
        }
        Path file = Paths.get (dir.toString (), "integration-framework.db");
        if (logger.isTraceEnabled ()) {
            logger.trace ("using database file: {}", file.toFile ().getCanonicalPath ());
        }
        sqlite = SQLite.get (file.toFile ().getCanonicalPath ());
        if (logger.isTraceEnabled ()) {
            logger.trace ("sqlite initialed as {}", sqlite);
        }
    }

    @Override
    public IDatabase getSystemDatabase () {
        return sqlite;
    }
}
