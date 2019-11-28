package org.kritikal.fabric.db.pgsql;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Configuration;
import org.kritikal.fabric.core.ConfigurationManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by ben on 9/7/14.
 */
public abstract class DbInstanceWorkerVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    public final boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;

    List<String> addresses;
    boolean readOnly;

    public DbInstanceContainer dbContainer = null;

    public Logger logger = null;
    List<MessageConsumer> mcList = null;

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        dbContainer = new DbInstanceContainer(ConfigurationManager.DEFAULT_CONCURRENCY);

        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return;
        }

        dbContainer.initialise(config().getJsonObject(config().getString("db_ref")));

        addresses = new ArrayList<String>();
        JsonArray ary = config().getJsonArray("addresses");
        for (int i = 0; i < ary.size(); ++i)
            addresses.add(ary.getString(i));
        readOnly = config().getBoolean("readOnly", false);
        mcList = new ArrayList<>();
        for(String address : addresses) {
            mcList.add(vertx.eventBus().localConsumer(address, this));
        }
    }

    public Connection connect() throws SQLException
    {
        return dbContainer.connect(vertx.fileSystem(), readOnly);
    }

    // TODO: move these out of here
    public JsonArray executeQuery(Connection con, String sql) throws SQLException
    {
        JsonArray ary = new JsonArray();
        // "SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL READ UNCOMMITTED READ ONLY DEFERRABLE;"
        PreparedStatement stmt = con.prepareStatement(sql);
        try {
            if (stmt.execute()) {
                ResultSet rs = stmt.getResultSet();
                try {
                    int l = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        JsonArray row = new JsonArray();
                        for (int i = 1; i <= l; ++i) {
                            Object o = rs.getObject(i);
                            if (o instanceof UUID)
                                row.add(((UUID) o).toString());
                            else if (o instanceof Timestamp)
                                row.add(((Timestamp) o).toString());
                            else
                                row.add(o);
                        }
                        ary.add(row);
                    }
                } finally {
                    rs.close();
                }
            }
        }
        finally {
            stmt.close();
        }
        return ary;
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
