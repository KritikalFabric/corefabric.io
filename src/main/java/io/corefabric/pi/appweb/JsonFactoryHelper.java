package io.corefabric.pi.appweb;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.net.http.CorsOptionsHandler;

/**
 * Created by ben on 11/6/16.
 */
public class JsonFactoryHelper {
    public static void addRoute(final String ROUTE, final Logger logger, final Router router, final CorsOptionsHandler corsOptionsHandler, final IJsonFactory factory) {
        router.options(ROUTE).handler(corsOptionsHandler);
        router.get(ROUTE).blockingHandler(rc -> {
            HttpServerRequest req = rc.request();
            req.response().headers().add("Content-Type", "application/json; charset=UTF-8");
            req.response().headers().add("Pragma", "no-cache");
            req.response().headers().add("Cache-control", "no-cache, no-store, private, must-revalidate");
            req.response().headers().add("Access-Control-Allow-Credentials", "true");
            String optionsOrigin = req.headers().get("Origin");
            if (optionsOrigin == null) optionsOrigin = "*";
            req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
            try {
                final byte[] payload = factory.apply().toString().getBytes("UTF-8");
                req.response().headers().add("Content-Length", Integer.toString(payload.length));
                req.response().write(Buffer.buffer(payload));
            }
            catch (Throwable t) {
                logger.warn(ROUTE, t);
            }
            req.response().end();
        });
    }
}
