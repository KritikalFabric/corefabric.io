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
    String connectionString;
    int delayMilliseconds;

    Logger logger = null;
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
        connectionString = config().getString("connectionString");
        delayMilliseconds = config().getInteger("delayMinutes") * 1000 * 60;

        try {
            ensureQueue(connectionString);
        }
        catch (Exception e) {
            logger.fatal("Cannot build tables.", e);
            return;
        }

        mcList = new ArrayList<>();
        for(String address : addresses) {
            mcList.add(vertx.eventBus().localConsumer(address, this));
        }
    }

    public static void ensureQueue(String connectionString) throws SQLException
    {
        Connection con = DriverManager.getConnection(connectionString);
        PreparedStatement stmt = con.prepareStatement("SELECT EXISTS(" +
                "    SELECT * " +
                "    FROM information_schema.tables " +
                "    WHERE " +
                "      table_schema = 'queues' AND " +
                "      table_name = 'send_q'" +
                "    )");
        try {
            boolean exists = false;
            if (stmt.execute()) {
                ResultSet rs = stmt.getResultSet();
                try {
                    if (rs.next()) {
                        exists = rs.getBoolean(1);
                    }
                } finally {
                    rs.close();
                }
            }
            if (!exists) {
                stmt = con.prepareStatement("CREATE SCHEMA IF NOT EXISTS queues");
                stmt.executeUpdate();
                stmt = con.prepareStatement("CREATE SEQUENCE queues.send_q_serial");
                stmt.executeUpdate();
                stmt = con.prepareStatement("CREATE TABLE queues.send_q ("
                        + "q_id bigint NOT NULL DEFAULT nextval('queues.send_q_serial'),"
                        + "a text NOT NULL,"
                        + "b jsonb NOT NULL,"
                        + "dt timestamptz NOT NULL)");
                stmt.executeUpdate();

                // TODO: CREATE INDEX idxgin ON api USING gin (jdoc); etc.
            }
        }
        finally {
            stmt.close();
        }
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
            Connection con = DriverManager.getConnection(connectionString);
            try
            {
                Timestamp ts = new Timestamp(new java.util.Date().getTime() + delayMilliseconds);
                PreparedStatement stmt = con.prepareStatement("INSERT INTO queues.send_q (a, b, dt) VALUES (?, ?::jsonb, ?)");
                try {
                    stmt.setString(1, message.address());
                    stmt.setString(2, message.body().toString());
                    stmt.setTimestamp(3, ts);
                    stmt.executeUpdate();
                }
                finally {
                    stmt.close();
                }
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
