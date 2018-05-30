package io.corefabric.pi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.net.http.BinaryBodyHandler;
import org.kritikal.fabric.net.http.CorsOptionsHandler;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;

public class AppConfigServerVerticle extends AbstractVerticle {

    final Logger logger = LoggerFactory.getLogger(AppConfigServerVerticle.class);
    HttpServer server = null;

    @Override
    public void start() throws Exception {
        super.start();

        if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-config-server\tStarting.");

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        server = vertx.createHttpServer(httpServerOptions);

        final Router router = Router.router(vertx);
        router.route().handler(new BinaryBodyHandler());
        final CorsOptionsHandler corsOptionsHandler = new CorsOptionsHandler();
        router.options().handler(corsOptionsHandler);
        router.get().handler(rc -> {
            HttpServerRequest req = rc.request();
            String[] path = req.path().split("/");
            int n = path[0] == null || path[0].length() == 0 ? 1 : 0;
            final String zone = path.length > n ? path[n] : ""; // default -- no zone specified
            final String instance = path.length > n + 1 ? path[n+1] : ""; // default -- no instance specified

            final JsonObject dbQuery = new JsonObject();
            dbQuery.put("action", "query");
            dbQuery.put("zone", zone);
            dbQuery.put("instance", instance);

            vertx.eventBus().send("corefabric.app-config-db", dbQuery, (ar) -> {
                JsonObject configuration = null;
                if (ar.succeeded()) {
                    configuration = (JsonObject)ar.result().body();
                } else {
                    configuration = new JsonObject();
                }
                try {
                    final String body = configuration.encode();
                    final byte[] bodyBytes = body.getBytes("utf-8");
                    req.response().headers().add("Cache-Control", "public, max-age=5");
                    req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                    req.response().headers().add("Content-Length", Long.toString(bodyBytes.length));
                    req.response().write(Buffer.buffer(bodyBytes)).end();
                    if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-config-server\t" + zone + "\t" + instance + "\t" + body);
                }
                catch (UnsupportedEncodingException uee) {
                    logger.fatal("app-config-server\t" + uee.getMessage());
                    req.response().setStatusCode(500);
                    req.response().end();
                }
            });
        });
        // TODO: post() handler to write configuration to the database
        // TODO: security
        server.requestHandler(req -> { router.accept(req); });
        server.listen(8082); // TODO: receive configuration from config.json
    }
}
