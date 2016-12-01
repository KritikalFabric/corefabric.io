package io.corefabric.pi.appweb.json;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.CoreFabric;
import io.corefabric.pi.appweb.IJsonFactory;
import io.corefabric.pi.appweb.JsonFactoryHelper;
import org.kritikal.fabric.net.http.CorsOptionsHandler;

/**
 * Created by ben on 11/6/16.
 */
public class DtnConfigJson implements IJsonFactory {
    final static String ROUTE = "/api/json/dtn-config";
    final static Logger logger = LoggerFactory.getLogger(DtnConfigJson.class);
    public static void addRoute(final Router router, final CorsOptionsHandler corsOptionsHandler){
        final IJsonFactory factory = new DtnConfigJson();
        JsonFactoryHelper.addRoute(ROUTE, logger, router, corsOptionsHandler, factory);
    }
    public final JsonObject apply(){
        JsonObject o = new JsonObject();
        JsonObject node = new JsonObject();
        node.put("hostname", CoreFabric.ServerConfiguration.hostname);
        o.put("node", node);
        o.put("dtn", CoreFabric.globalConfig.getJsonObject("dtn", new JsonObject()));
        o.getJsonObject("dtn").put("links", new JsonArray());
        o.getJsonObject("dtn").put("neighbors", new JsonArray());
        o.put("dtn-mqtt-bridge", CoreFabric.globalConfig.getJsonObject("roles", new JsonObject()).getJsonArray("dtn-mqtt-bridge", new JsonArray()));
        return o;
    }
}
