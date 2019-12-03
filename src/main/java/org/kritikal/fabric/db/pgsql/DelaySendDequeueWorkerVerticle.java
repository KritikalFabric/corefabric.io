package org.kritikal.fabric.db.pgsql;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import org.kritikal.fabric.core.VERTXDEFINES;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by ben on 5/24/14.
 */
public class DelaySendDequeueWorkerVerticle extends AbstractVerticle {

    private final static ConcurrentHashMap<String, BasicDataSource> poolOfPools = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> taskHandle;
    List<String> addresses;
    public DbInstanceContainer dbContainer = new DbInstanceContainer(1);

    Logger logger;

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

        try {
            ensureDequeue();
        }
        catch (Exception e) {
            logger.fatal("Cannot build tables.", e);
            return;
        }

        // Start worker thread
        taskHandle = scheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try
                {
                    final Connection con = dbContainer.connect(getVertx().fileSystem(), false);
                    try
                    {
                        StringBuilder array = new StringBuilder();
                        array.append("ARRAY[");
                        for (int i = 0; i < addresses.size(); ++i) {
                            array.append(i == 0 ? "?" : ",?");
                        }
                        array.append("]");
                        //PreparedStatement stmt = con.prepareStatement("LOCK TABLE node.send_q IN EXCLUSIVE MODE NOWAIT; SELECT a, b FROM node.dequeue_send_q(" +
                        PreparedStatement stmt = con.prepareStatement("SELECT a, b FROM node.dequeue_send_q(" +
                                array.toString() + ");");
                        try {
                            int i = 0;
                            for (String address : addresses)  {
                                stmt.setString(++i, address);
                            }
                            if (stmt.execute()) {
                                ResultSet rs = stmt.getResultSet();
                                try {
                                    while (rs.next()) {
                                        try {
                                            String address = rs.getString(1);
                                            String body = rs.getString(2);
                                            final JsonObject o = new JsonObject(body);
                                            vertx.eventBus().send(address, o, VERTXDEFINES.DELIVERY_OPTIONS);
                                        }
                                        catch (Exception e)
                                        {
                                            logger.warn("Unable to deserialise body of message", e);
                                        }
                                    }
                                } finally {
                                    rs.close();
                                }
                            }
                            con.commit();
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
                    logger.warn("In run()", e);
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void stop() throws Exception
    {
        taskHandle.cancel(false);
        super.stop();
    }

    public static void ensureDequeue() throws Exception
    {
    }

}
