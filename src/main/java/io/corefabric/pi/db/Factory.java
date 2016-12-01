package io.corefabric.pi.db;

import io.vertx.core.file.FileSystem;
import org.kritikal.fabric.db.pgsql.DbContainer;

import java.sql.Connection;

/**
 * Created by ben on 18/06/2016.
 */
public class Factory {
    public static boolean needsUpgrade(String s) { return false; }
    public static int upgradeIfNeeded(String s, Connection c, FileSystem fileSystem) { return 0; }
    public static void addDbContainer(DbContainer dbContainer) {}
    public static void removeDbContainer(DbContainer dbContainer) {}
}
