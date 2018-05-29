package io.corefabric.pi.db;

import io.vertx.core.file.FileSystem;
import org.kritikal.fabric.db.pgsql.DbContainer;
import org.kritikal.fabric.db.pgsql.DbInstanceContainer;

import java.sql.Connection;

/**
 * Created by ben on 18/06/2016.
 */
public class Factory {
    public static boolean needsUpgrade(String schema) { return false; }
    public static boolean needsUpgrade(String schema, String instanceKey) { return false; }
    public static int upgradeIfNeeded(String schema, Connection c, FileSystem fileSystem) { return 0; }
    public static int upgradeIfNeeded(String schema, String instanceKey, Connection c, FileSystem fileSystem) { return 0; }
    public static void addDbContainer(DbContainer dbContainer) {}
    public static void removeDbContainer(DbContainer dbContainer) {}
    public static void addDbInstanceContainer(DbInstanceContainer dbContainer) {}
    public static void removeDbInstanceContainer(DbInstanceContainer dbContainer) {}
}
