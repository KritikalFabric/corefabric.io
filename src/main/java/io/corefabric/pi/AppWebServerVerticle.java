package io.corefabric.pi;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.core.VERTXDEFINES;
import io.corefabric.pi.appweb.json.DtnConfigJson;
import io.corefabric.pi.appweb.json.NodeMonitorJson;
import io.corefabric.pi.appweb.json.StatusJson;
import org.kritikal.fabric.net.http.BinaryBodyHandler;
import org.kritikal.fabric.net.http.CorsOptionsHandler;
import org.kritikal.fabric.net.mqtt.MqttBroker;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Created by ben on 11/3/16.
 */
public class AppWebServerVerticle extends AbstractVerticle {

    final Logger logger = LoggerFactory.getLogger(AppWebServerVerticle.class);
    HttpServer server = null;

    private static String cookieCutter(HttpServerRequest req) {
        String corefabric = null;
        try {
            String cookies = req.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if ("corefabric".equals(cookie.getName())) {
                    corefabric = cookie.getValue().trim();
                    break;
                }
            }
            if ("".equals(corefabric)) {
                corefabric = null;
            }
            UUID.fromString(corefabric); // does it parse?
        } catch (Throwable t) {
            corefabric = null;
        }
        if (corefabric == null) {
            corefabric = UUID.randomUUID().toString();
            Cookie cookie = new DefaultCookie("corefabric", corefabric);
            req.response().headers().add("Set-Cookie", ServerCookieEncoder.encode(cookie));
        }
        return corefabric;
    }

    private static String cookieCutter(ServerWebSocket webSocket) {
        String corefabric = null;
        try {
            String cookies = webSocket.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if ("corefabric".equals(cookie.getName())) {
                    corefabric = cookie.getValue().trim();
                    UUID.fromString(corefabric); // does it parse?
                    return corefabric;
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    @Override
    public void start() throws Exception {
        super.start();

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        server = vertx.createHttpServer(httpServerOptions);
        server.websocketHandler(ws -> {
            final String corefabric = cookieCutter(ws);
            if (corefabric==null) ws.reject();
            if (!"/mqtt".equals(ws.path())) ws.reject();
            else {
                final MqttBroker mqttBroker = (MqttBroker)MqttBrokerVerticle.mqttBroker();//hack
                final MqttBroker.MyMqttServerProtocol mqttServerProtocol = new MqttBroker.MyMqttServerProtocol(logger, getVertx(), mqttBroker, ws, corefabric);
                mqttBroker.waitingForConnect.add(mqttServerProtocol);
            }
        });
        final Router router = Router.router(vertx);
        router.route().handler(new BinaryBodyHandler());
        final CorsOptionsHandler corsOptionsHandler = new CorsOptionsHandler();
        router.options("/api/doc").handler(corsOptionsHandler);
        NodeMonitorJson.addRoute(router, corsOptionsHandler);
        DtnConfigJson.addRoute(router, corsOptionsHandler);
        StatusJson.addRoute(router, corsOptionsHandler);
        router.post("/api/doc").blockingHandler(rc -> {
            HttpServerRequest req = rc.request();
            JsonObject body = rc.getBodyAsJson();

            if (body == null) {
                req.response().setStatusCode(500).end();
                return;
            }

            //String host = req.headers().get("Host");
            //String instancekey = host.split("\\.")[0];
            final String instancekey = "demo"; //FIXME

            final String corefabric = cookieCutter(req);

            JsonObject o = new JsonObject();
            o.put("instancekey", instancekey);
            o.put("payload", body);
            o.put("corefabric", corefabric);

            vertx.eventBus().send("ui.docapi", o, VERTXDEFINES.DELIVERY_OPTIONS, new Handler<AsyncResult<Message<JsonObject>>>() {
                @Override
                public void handle(AsyncResult<Message<JsonObject>> asyncResult) {
                    if (asyncResult.succeeded()) {
                        final JsonObject event = asyncResult.result().body();
                        req.response().headers().add("Access-Control-Allow-Credentials", "true");
                        String optionsOrigin = req.headers().get("Origin");
                        if (optionsOrigin == null) optionsOrigin = "*";
                        req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
                        req.response().headers().add("Content-Type", "application/json; charset=UTF-8");
                        req.response().end(event.encode());
                    } else {
                        req.response().setStatusCode(500).end();
                    }
                }
            });
        });
        router.get().blockingHandler(rc -> {
            HttpServerRequest req = rc.request();
            if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-web-server\t" + req.path());

            String file = null;
            if (req.path().contains("..")) {
                req.response().setStatusCode(500);
                req.response().end();
                return;
            } else if (req.path().equals("/")) {
                file = "index.html";
            } else if (req.path().startsWith("/")) {
                file = req.path().substring(1);
            } else {
                req.response().setStatusCode(404);
                req.response().end();
                return;
            }

            if (file != null) {

                cookieCutter(req);

                if (file.endsWith(".html")) {
                    req.response().headers().add("Pragma", "no-cache");
                    req.response().headers().add("Cache-control", "no-cache, no-store, private, must-revalidate");
                } else {
                    req.response().headers().add("Cache-control", "cache, store");
                }

                InputStream fileStream = getClass().getClassLoader().getResourceAsStream("web/a2/" + file); // <-- blocking api call
                if (fileStream == null) {
                    if (file.endsWith(".js") ||
                            file.endsWith(".map") ||
                            file.endsWith(".css") ||
                            file.endsWith(".less") ||
                            file.endsWith(".json") ||
                            file.endsWith(".png") ||
                            file.endsWith(".jpg") ||
                            file.endsWith(".jpeg") ||
                            file.endsWith(".gif") ||
                            file.endsWith(".txt") ||
                            file.endsWith(".html")) {
                        req.response().setStatusCode(404).setStatusMessage("Not Found");
                        req.response().end();
                        return;
                    }
                    file = "index.html";
                    fileStream = getClass().getClassLoader().getResourceAsStream("web/a2/index.html");
                }
                if (fileStream != null) {
                    try {
                        Buffer buffer = Buffer.buffer();
                        byte[] b = new byte[1024];
                        int n;
                        try {
                            while ((n = fileStream.read(b)) > 0) {
                                buffer.appendBytes(b, 0, n);
                            }
                            req.response().headers().add("Content-Length", Long.toString(buffer.length()));
                            if (file.endsWith(".html")) {
                                req.response().headers().add("Content-Type", "text/html; charset=UTF-8");
                            } else if (file.endsWith(".js")) {
                                req.response().headers().add("Content-Type", "text/javascript; charset=UTF-8");
                            } else if (file.endsWith(".json")) {
                                req.response().headers().add("Content-Type", "application/json; charset=UTF-8");
                            } else if (file.endsWith(".css")) {
                                req.response().headers().add("Content-Type", "text/css; charset=UTF-8");
                            } else if (file.endsWith(".txt")) {
                                req.response().headers().add("Content-Type", "text/plain; charset=UTF-8");
                            } else {
                                req.response().headers().add("Content-Type", "application/octet-stream");
                            }
                            req.response().write(buffer).end();
                        } catch (Throwable t) {
                            req.response().setStatusCode(500).end();
                        }
                    } finally {
                        try {
                            fileStream.close();
                        } catch (Throwable t) { /* ignore */ }
                    }
                }
                else {
                    // we can't get here!
                    req.response().setStatusCode(302).setStatusMessage("Found").headers().add("Location", "/");
                    req.response().end();
                }
            }
            else {
                req.response().setStatusCode(500).end();
            }
        });
        server.requestHandler(req -> { router.accept(req); });
        server.listen(1080);
    }
}
