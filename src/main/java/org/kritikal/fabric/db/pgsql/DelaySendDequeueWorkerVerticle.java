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
import java.util.concurrent.*;

/**
 * Created by ben on 5/24/14.
 */
public class DelaySendDequeueWorkerVerticle extends AbstractVerticle {

    private final static ConcurrentHashMap<String, BasicDataSource> poolOfPools = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> taskHandle;
    ConcurrentHashMap<String, String> addresses;
    String connectionString;
    Logger logger;

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return;
        }

        addresses = new ConcurrentHashMap<String, String>();
        JsonObject obj = config().getJsonObject("addresses");
        for (String fieldName : obj.fieldNames())
        {
            addresses.put(fieldName, obj.getString(fieldName));
        }

        connectionString = config().getString("connectionString");

        try {
            DelaySendEnqueueWorkerVerticle.ensureQueue(connectionString);
            ensureDequeue(connectionString);
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
                    final Connection con = poolOfPools.computeIfAbsent(connectionString, (k) -> {
                        return BasicDataSourceHelper.pool(1, basicDataSource -> {
                                    basicDataSource.setUrl(connectionString);
                                    basicDataSource.setUsername("postgres");
                                    basicDataSource.setPassword("password");
                                    basicDataSource.setAccessToUnderlyingConnectionAllowed(true);
                                });
                    }).getConnection();
                    try
                    {
                        StringBuilder array = new StringBuilder();
                        array.append("ARRAY[");
                        for (int i = 0; i < addresses.size(); ++i) {
                            array.append(i == 0 ? "?" : ",?");
                        }
                        array.append("]");
                        PreparedStatement stmt = con.prepareStatement("LOCK TABLE node.send_q IN EXCLUSIVE MODE NOWAIT; SELECT a, b FROM node.dequeue_send_q(" +
                                array.toString() + ");");
                        try {
                            int i = 0;
                            for (String address : addresses.keySet())  {
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
                                            String toAddress = addresses.get(address);
                                            vertx.eventBus().send(toAddress, o, VERTXDEFINES.DELIVERY_OPTIONS);
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
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() throws Exception
    {
        taskHandle.cancel(false);
        super.stop();
    }

    public static void ensureDequeue(String connectionString) throws Exception
    {
    }

}
