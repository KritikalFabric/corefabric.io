package io.corefabric.pi.appweb.json;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.corefabric.pi.appweb.IJsonFactory;
import io.corefabric.pi.appweb.JsonFactoryHelper;
import org.kritikal.fabric.net.http.CorsOptionsHandler;

/**
 * Created by ben on 11/8/16.
 */
public class StatusJson implements IJsonFactory {
    final static String ROUTE = "/api/json/status";
    final static Logger logger = LoggerFactory.getLogger(StatusJson.class);
    public static void addRoute(final Router router, final CorsOptionsHandler corsOptionsHandler){
        final IJsonFactory factory = new StatusJson();
        JsonFactoryHelper.addRoute(ROUTE, logger, router, corsOptionsHandler, factory);
    }
    public final JsonObject apply(){
        JsonObject o = new JsonObject();
        o.put("up", true);
        return o;
    }
}