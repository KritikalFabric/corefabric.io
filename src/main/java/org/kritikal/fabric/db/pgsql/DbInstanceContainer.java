package org.kritikal.fabric.db.pgsql;

import io.corefabric.pi.db.Factory;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import org.kritikal.fabric.contrib.db.CFPostgresqlPoolHelper;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by ben on 03/06/15.
 */
public class DbInstanceContainer {

    private final static Logger logger = LoggerFactory.getLogger(DbInstanceContainer.class);

    private final int concurrency;

    public DbInstanceContainer(int concurrency) {
        this.concurrency = concurrency;
        Factory.addDbInstanceContainer(this);
    }

    public void remove() {
        Factory.removeDbInstanceContainer(this);
    }

    private JsonObject configuration = null;
    public void initialise(JsonObject configuration) {
        this.configuration = configuration;
    }

    private BasicDataSource dataSource;

    public Connection connect(FileSystem fileSystem, boolean readOnly) throws SQLException
    {
        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return null;
        }

        if (null == dataSource) {
            synchronized (this) {
                if (null == dataSource) {
                    String host = configuration.getString("host", "localhost");
                    int port = configuration.getInteger("port", 5432);
                    String db = configuration.getString("db");
                    String user = configuration.getString("user", "postgres");
                    String password = configuration.getString("password", "password");

                    logger.info("jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8");

                    dataSource = CFPostgresqlPoolHelper.pool(concurrency, basicDataSource1 -> {
                        basicDataSource1.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8");
                        basicDataSource1.setUsername(user);
                        basicDataSource1.setPassword(password);
                        basicDataSource1.setAccessToUnderlyingConnectionAllowed(true);
                        basicDataSource1.setDefaultReadOnly(readOnly);
                    });

                    if (Factory.needsUpgrade("config") && !readOnly) {
                        try {
                            Connection conUpgrade = dataSource.getConnection();
                            conUpgrade.setAutoCommit(true);
                            try {
                                Factory factory = new Factory();
                                int version = factory.upgradeIfNeeded("config", conUpgrade, fileSystem);
                                if (version > 0) {
                                    logger.info("config" + "\tdb\tUpgraded to version " + version);
                                }
                            } finally {
                                conUpgrade.close();
                            }
                        } catch (Throwable t) {
                            logger.fatal("config" + "\tdb\tDuring upgrade", t);
                        }
                    }
                }
            }
        }

        return dataSource.getConnection();
    }

    public void close() {
        if (null != dataSource) {
            synchronized (this) {
                if (null != dataSource) {
                    try {
                        dataSource.close();
                    } catch (SQLException e) {
                        // nothing
                    } finally {
                        dataSource = null;
                    }
                }
            }
        }
    }
}
