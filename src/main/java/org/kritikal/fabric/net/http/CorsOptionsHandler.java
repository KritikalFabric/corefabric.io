package org.kritikal.fabric.net.http;

import java.net.URI;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Created by ben on 4/12/16.
 */
public class CorsOptionsHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext event) {
        HttpServerRequest req = event.request();

        String optionsOrigin = req.getHeader("Origin");
        if (null != optionsOrigin  && !"".equals(optionsOrigin)) {
            req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
            req.response().headers().add("Access-Control-Allow-Credentials", "true");
        }
        else {
            req.response().headers().add("Access-Control-Allow-Origin", "*");
        }
        req.response().headers().add("Access-Control-Allow-Methods", "GET, POST");
        req.response().headers().add("Access-Control-Allow-Headers", "X-Preflight, Content-Type");
        req.response().headers().add("Access-Control-Max-Age", "60");
        req.response().headers().add("Vary", "Accept-Encoding, Origin");
        req.response().end();
    }

    public void applyResponseHeaders(HttpServerRequest req, boolean cors, boolean cache) {
        req.response().headers().add("Content-Type", "application/json");
        if (cache) {
            req.response().setStatusCode(200).setStatusMessage("OK");
            req.response().headers().add("Cache-Control", "cache, store, private, must-revalidate, max-age=604800"); // 7 days
        } else {
            req.response().headers().add("Pragma", "no-cache");
            req.response().headers().add("Cache-control", "no-cache, no-store, private, must-revalidate");
        }
        if (cors) {
            req.response().headers().add("Access-Control-Allow-Credentials", "true");
            String optionsOrigin = req.headers().get("Origin");
            if (optionsOrigin == null) optionsOrigin = "*";
            req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
        }
    }
}
