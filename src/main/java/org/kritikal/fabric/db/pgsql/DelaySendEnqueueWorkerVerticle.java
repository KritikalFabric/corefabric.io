package org.kritikal.fabric.db.pgsql;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Created by ben on 5/24/14.
 */
public class DelaySendEnqueueWorkerVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    List<String> addresses;
    int delayMilliseconds;

    Logger logger = null;
    List<MessageConsumer> mcList = null;

    public DbInstanceContainer dbContainer = new DbInstanceContainer(1);

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return;
        }

        addresses = new ArrayList<String>();
        addresses.add(config().getString("destination"));
        dbContainer.initialise(config().getJsonObject(config().getString("db_ref")));
        delayMilliseconds = config().getInteger("delayMinutes") * 1000 * 60;

        try {
            ensureQueue();
        }
        catch (Exception e) {
            logger.fatal("Cannot build tables.", e);
            return;
        }

        mcList = new ArrayList<>();
        mcList.add(vertx.eventBus().localConsumer(config().getString("listen"), this));
    }

    public static void ensureQueue() throws SQLException
    {
    }

    public void stop() throws Exception
    {
        if (mcList != null)
            for (MessageConsumer mc : mcList)
                mc.unregister();
        mcList = null;

        super.stop();
    }

    public void handle(Message<JsonObject> message)
    {
        try
        {
            Connection con = dbContainer.connect(getVertx().fileSystem(), false);
            try
            {
                Timestamp ts = new Timestamp(new java.util.Date().getTime() + delayMilliseconds);
                PreparedStatement stmt = con.prepareStatement("INSERT INTO node.send_q (a, b, dt) VALUES (?, ?::jsonb, ?)");
                try {
                    stmt.setString(1, config().getString("destination"));
                    stmt.setString(2, message.body().toString());
                    stmt.setTimestamp(3, ts);
                    stmt.executeUpdate();
                }
                finally {
                    stmt.close();
                }

                con.commit();
            }
            finally
            {
                con.close();
            }
        }
        catch (Exception e)
        {
            logger.warn("Handling message to " + message.address(), e);
        }
    }
}
