package io.corefabric.pi.db;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.db.pgsql.DbInstanceWorkerVerticle;
import org.kritikal.fabric.db.pgsql.PgDbHelper;

import java.sql.Connection;
import java.sql.SQLException;

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
        final JsonObject dbQuery = message.body();
        final String action = dbQuery.getString("action", "query");
        final String zone = dbQuery.getString("zone");
        final String instance = dbQuery.getString("instance");
        switch (action) {
            case "query":
                actionQuery(message, dbQuery, zone, instance);
                break;
        }
    }

    private void actionQuery(Message<JsonObject> message, final JsonObject dbQuery, final String zone, final String instance) {
        JsonObject zoneObject = null;
        JsonObject instanceObject = null;
        final String query = "SELECT z.object::text, n.object::text FROM config.zonejson z LEFT OUTER JOIN config.instancejson n ON (z.id = n.zone_id) WHERE z.zone = " + PgDbHelper.quote(zone) + " AND n.instance = " + PgDbHelper.quote(instance);
        try {
            final Connection con = connect();
            try {
                JsonArray results = executeQuery(con, query);
                if (results.size() > 0) {
                    JsonArray row = results.getJsonArray(0);
                    if (row.size() >= 2) {
                        String zoneObjectString = row.getString(0);
                        String instanceObjectString = row.getString(1);
                        zoneObject = new JsonObject(null == zoneObjectString ? "{\"active\":false}" : zoneObjectString);
                        instanceObject = new JsonObject(null == instanceObjectString ? "{\"active\":false}" : instanceObjectString);
                    }
                }
            } finally {
                try {
                    con.close();
                } catch (SQLException ignore) {
                }
            }
        }
        catch (SQLException rethrow) {
            throw new RuntimeException(rethrow);
        }
        zoneObject.put("name", zone);
        instanceObject.put("name", instance);
        JsonObject replyObject = new JsonObject();
        replyObject.put("zone", zoneObject);
        replyObject.put("instance", instanceObject);
        message.reply(replyObject);
    }
}
