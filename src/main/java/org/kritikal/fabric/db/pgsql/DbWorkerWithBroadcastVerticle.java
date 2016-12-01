package org.kritikal.fabric.db.pgsql;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.Configuration;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.CoreFabric;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ben on 23/05/15.
 */
public abstract class DbWorkerWithBroadcastVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {


    public final boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;

    List<String> addresses;
    boolean readOnly;

    final static DbContainer dbContainer = new DbContainer(ConfigurationManager.DEFAULT_CONCURRENCY);

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
        readOnly = config().getBoolean("readOnly");
        mcList = new ArrayList<>();
        for(String address : addresses) {
            mcList.add(vertx.eventBus().localConsumer(address, this));
        }
        for(String address : addresses) {
            mcList.add(vertx.eventBus().localConsumer(address + "-broadcast", new Handler<Message<JsonObject>>() {
                @Override
                public void handle(Message<JsonObject> event) {
                    handleBroadcast(event);
                }
            }));
        }
    }

    public final Connection connect(Configuration cfg) throws SQLException
    {
        return dbContainer.connect(cfg, vertx.fileSystem(), readOnly);
    }

    public void stop() throws Exception
    {
        if (mcList != null)
            for (MessageConsumer mc : mcList)
                mc.unregister();
        mcList = null;

        dbContainer.close();

        super.stop();
    }

    public abstract void handle(Message<JsonObject> message);

    public abstract void handleBroadcast(Message<JsonObject> message);

}
