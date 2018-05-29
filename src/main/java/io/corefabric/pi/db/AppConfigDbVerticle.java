package io.corefabric.pi.db;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.db.pgsql.DbInstanceWorkerVerticle;

public class AppConfigDbVerticle extends DbInstanceWorkerVerticle {

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }

    @Override
    public void handle(Message<JsonObject> message) {

    }
}
