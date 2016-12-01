package org.kritikal.fabric.db.pgsql;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.CoreFabric;
import org.apache.commons.dbcp2.BasicDataSource;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.core.Configuration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ben on 22/05/15.
 */
public abstract class DbAdminWorkerVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    public final boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;

    List<String> addresses;

    public Logger logger = null;
    List<MessageConsumer> mcList = null;

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return;
        }

        addresses = new ArrayList<String>();
        JsonArray ary = config().getJsonArray("addresses");
        for (int i = 0; i < ary.size(); ++i)
            addresses.add(ary.getString(i));
        mcList = new ArrayList<>();
        for(String address : addresses) {
            mcList.add(vertx.eventBus().localConsumer(address, this));
        }
    }

    public static Connection connect(Configuration cfg) throws SQLException
    {
        return DriverManager.getConnection(cfg.getAdminConnectionString() + "&user=" + cfg.getDbUser() + "&password=" + cfg.getDbPassword());
    }

    public static Connection getConnection(Configuration cfg) throws SQLException
    {
        return getConnection(cfg.getConnectionString(), cfg.getDbUser(), cfg.getDbPassword());
    }

    private static ConcurrentHashMap<String, BasicDataSource> poolOfPools = new ConcurrentHashMap<>();

    public static Connection getConnection(String connectionString, String username, String password) throws SQLException {
        return poolOfPools.computeIfAbsent(connectionString, (k) -> {
            return BasicDataSourceHelper.pool(ConfigurationManager.DEFAULT_CONCURRENCY, basicDataSource -> {
                        basicDataSource.setUrl(connectionString);
                        basicDataSource.setUsername(username);
                        basicDataSource.setPassword(password);
                        basicDataSource.setAccessToUnderlyingConnectionAllowed(true);
                    });
        }).getConnection();
    }

    public void stop() throws Exception
    {
        if (mcList != null)
            for (MessageConsumer mc : mcList)
                mc.unregister();
        mcList = null;

        super.stop();
    }

    public abstract void handle(Message<JsonObject> message);
}