package org.kritikal.fabric.db.pgsql;

import io.vertx.core.Vertx;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.corefabric.pi.db.Factory;
import org.kritikal.fabric.core.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Created by ben on 03/06/15.
 */
public class DbContainer {

    private final static Logger logger = LoggerFactory.getLogger(DbContainer.class);

    private final int concurrency;

    public DbContainer(int concurrency) {
        this.concurrency = concurrency;
        Factory.addDbContainer(this);
    }

    public void remove() {
        Factory.removeDbContainer(this);
    }

    private ConcurrentHashMap<String, BasicDataSource> poolOfPools = new ConcurrentHashMap<>();

    public Connection connect(Configuration cfg, FileSystem fileSystem, boolean readOnly) throws SQLException
    {
        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return null;
        }
        return poolOfPools.computeIfAbsent(cfg.instancekey, new Function<String, BasicDataSource>() {
                @Override
                public BasicDataSource apply(String s) {
                    BasicDataSource basicDataSource = BasicDataSourceHelper.pool(concurrency, basicDataSource1 -> {
                        basicDataSource1.setUrl(cfg.getMiniConnectionString());
                        basicDataSource1.setUsername(cfg.getDbUser());
                        basicDataSource1.setPassword(cfg.getDbPassword());
                        basicDataSource1.setAccessToUnderlyingConnectionAllowed(true);
                        basicDataSource1.setDefaultReadOnly(readOnly);
                    });

                    if (Factory.needsUpgrade("node", cfg.instancekey) && !readOnly) {
                        try {
                            Connection conUpgrade = basicDataSource.getConnection();
                            conUpgrade.setAutoCommit(true);
                            try {
                                Factory factory = new Factory();
                                int version = factory.upgradeIfNeeded("node", cfg.instancekey, conUpgrade, fileSystem);
                                if (version > 0) {
                                    logger.info("node\t" + cfg.instancekey + "\tdb\tUpgraded to version " + version);
                                }
                            }
                            finally {
                                conUpgrade.close();
                            }
                        } catch (Throwable t) {
                            logger.fatal("node\t" + cfg.instancekey + "\tdb\tDuring upgrade", t);
                        }
                    }

                    return basicDataSource;
                }
            }).getConnection();
    }

    public void close() {
        ConcurrentHashMap<String, BasicDataSource> pool = poolOfPools;
        poolOfPools = new ConcurrentHashMap<>();
        for (BasicDataSource basicDataSource : pool.values()) {
            try {
                basicDataSource.close();
            }
            catch (SQLException e) {
                // nothing
            }
        }
    }

    public void remove(String id) {
        poolOfPools.remove(id);
    }

    public void execute(Configuration cfg, FileSystem fileSystem, DbTask task, Vertx vertx, JsonObject config) {
        Connection con = null;
        try {
            con = connect(cfg, fileSystem, false);
            if (con != null) {
                task.execute(con, cfg, vertx, config);
            }
            con.commit();
        }
        catch (Throwable t) {
            logger.error(cfg.instancekey + "\tdb-container\tWhile executing " + task.getName(), t);
            if (con != null) try { con.rollback(); } catch (SQLException e) { }
        }
        finally {
            if (con != null) try { con.close(); } catch (SQLException e) { }
            con = null;
        }
    }

}
