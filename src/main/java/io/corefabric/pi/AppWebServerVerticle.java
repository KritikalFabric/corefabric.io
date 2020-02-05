package io.corefabric.pi;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.core.VERTXDEFINES;
import io.corefabric.pi.appweb.json.DtnConfigJson;
import io.corefabric.pi.appweb.json.NodeMonitorJson;
import io.corefabric.pi.appweb.json.StatusJson;
import org.kritikal.fabric.net.http.*;
import org.kritikal.fabric.net.mqtt.SyncMqttBroker;

import java.io.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Created by ben on 11/3/16.
 */
public class AppWebServerVerticle extends AbstractVerticle {

    final Logger logger = LoggerFactory.getLogger(AppWebServerVerticle.class);
    HttpServer server = null;
    String tempdir = null;
    boolean runningInsideJar = true;

    private static void extract(JarFile parentJar, ZipEntry entry, String destination)
        throws java.io.FileNotFoundException, java.io.IOException
    {
        BufferedInputStream is = new BufferedInputStream(parentJar.getInputStream(entry));
        try {
            File f = new File(destination);
            String parentName = f.getParent();
            if (parentName != null) {
                File dir = new File(parentName);
                dir.mkdirs();
            }

            BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(f));
            try {
                int c;
                while ((c = is.read()) != -1) {
                    os.write((byte) c);
                }
            }
            finally {
                os.close();
            }
        }
        finally {
            is.close();
        }
    }

    private static void sendFile(HttpServerRequest req, String pathToFile, boolean acceptEncodingGzip) {
        if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Pragma", "no-cache");
            req.response().headers().add("Cache-Control", "no-cache, no-store, private, must-revalidate");
        } else {
            req.response().headers().add("Cache-Control", "public, max-age=7200");
        }

        if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Content-Type", "text/html; charset=utf-8");
        } else if (pathToFile.endsWith(".js")) {
            req.response().headers().add("Content-Type", "text/javascript; charset=utf-8");
        } else if (pathToFile.endsWith(".json")) {
            req.response().headers().add("Content-Type", "application/json");
        } else if (pathToFile.endsWith(".css")) {
            req.response().headers().add("Content-Type", "text/css; charset=utf-8");
        } else if (pathToFile.endsWith(".txt")) {
            req.response().headers().add("Content-Type", "text/plain; charset=utf-8");
        } else {
            req.response().headers().add("Content-Type", "application/octet-stream");
        }

        if (acceptEncodingGzip) {
            req.response().headers().add("Content-Encoding", "gzip");
        }

        req.response().sendFile(pathToFile + (acceptEncodingGzip ? ".gz" : ""));
    }

    /* FIXME: stop() is not called

    @Override
    public void stop() throws Exception {
        server.close();
        server = null;
        if (runningInsideJar) {
            FileUtils.deleteDirectory(new File(tempdir));
            if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-web-server\tCleaned:\t" + tempdir);
        }
        super.stop();
    }

    */

    @Override
    public void start() throws Exception {
        super.start();

        UUID uuid = UUID.randomUUID();
        tempdir = ConfigurationManager.Shim.tmp + "/corefabric__" + uuid.toString() + "/";
        try {
            File file = new File(tempdir);
            file.mkdirs();
        }
        catch (Throwable t) {
            // ignore
        }

        String runningJar = new File(AppWebServerVerticle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        //if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-web-server\tCode running from:\t" + runningJar);
        runningInsideJar = runningJar.endsWith("-fat.jar");
        if (runningInsideJar) {
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(runningJar);
                Enumeration entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    final String[] parts = entry.getName().split("/");
                    if (parts.length < 3) continue;
                    if (!"web".equals(parts[0])) continue;
                    if (!"a2".equals(parts[1])) continue;
                    if (entry.isDirectory()) continue;

                    StringBuilder newPath = new StringBuilder();
                    newPath.append(tempdir);
                    for (int i = 2; i < parts.length; ++i) {
                        if (i > 2) newPath.append("/");
                        newPath.append(parts[i]);
                    }

                    extract(jarFile, entry, newPath.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (CoreFabric.ServerConfiguration.DEBUG) logger.info("app-web-server\tServing from: " + (runningInsideJar ? tempdir : "a2/dist/a2"));

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        server = vertx.createHttpServer(httpServerOptions);
        server.websocketHandler(ws -> {
            final CFCookie corefabric = AngularIOWebContainer.cookieCutter(ws);
            if (corefabric.is_new) ws.reject();
            if (!"/mqtt".equals(ws.path())) ws.reject();
            else {
                final SyncMqttBroker mqttBroker = (SyncMqttBroker)MqttBrokerVerticle.syncBroker();//hack
                final SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = new SyncMqttBroker.MyMqttServerProtocol(logger, getVertx(), mqttBroker, ws, corefabric);
                mqttBroker.waitingForConnect.add(mqttServerProtocol);
            }
        });
        final Router router = Router.router(vertx);
        router.route().handler(new BinaryBodyHandler());
        final CorsOptionsHandler corsOptionsHandler = new CorsOptionsHandler();
        FabricApiConnector.connectRestApi(io.corefabric.pi.appweb.apis.ItemRestApi.class, router, corsOptionsHandler);
        router.options("/api/doc").handler(corsOptionsHandler);
        NodeMonitorJson.addRoute(router, corsOptionsHandler);
        DtnConfigJson.addRoute(router, corsOptionsHandler);
        StatusJson.addRoute(router, corsOptionsHandler);
        router.post("/api/doc").handler(rc -> {
            HttpServerRequest req = rc.request();
            JsonObject body = rc.getBodyAsJson();

            if (body == null) {
                req.response().setStatusCode(500).end();
                return;
            }

            //String host = req.headers().get("Host");
            //String instancekey = host.split("\\.")[0];
            final String instancekey = "development/demo"; //FIXME zone/instance

            final CFCookie corefabric = AngularIOWebContainer.cookieCutter(req);

            JsonObject o = new JsonObject();
            o.put("instancekey", instancekey);
            o.put("payload", body);
            o.put("corefabric", corefabric.session_uuid.toString());

            vertx.eventBus().send("ui.docapi", o, VERTXDEFINES.DELIVERY_OPTIONS, new Handler<AsyncResult<Message<JsonObject>>>() {
                @Override
                public void handle(AsyncResult<Message<JsonObject>> asyncResult) {
                    if (asyncResult.succeeded()) {
                        final JsonObject event = asyncResult.result().body();
                        req.response().headers().add("Access-Control-Allow-Credentials", "true");
                        String optionsOrigin = req.headers().get("Origin");
                        if (optionsOrigin == null) optionsOrigin = "*";
                        req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
                        req.response().headers().add("Content-Type", "application/json");
                        req.response().end(event.encode());
                    } else {
                        req.response().setStatusCode(500).end();
                    }
                }
            });
        });
        router.get().handler(rc -> {
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

                AngularIOWebContainer.cookieCutter(req);

                boolean gzip = false;
                for (Map.Entry<String, String> stringStringEntry : req.headers()) {
                    if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                            stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                        gzip = true;
                        break;
                    }
                }
                final boolean acceptEncodingGzip = gzip;

                final String filesystemLocation = (runningInsideJar ? tempdir : "a2/dist/a2/") + file;
                final String indexHtmlLocation = (runningInsideJar ? tempdir : "a2/dist/a2/") + "index.html";
                vertx.fileSystem().exists(filesystemLocation + (acceptEncodingGzip ? ".gz" : ""), new Handler<AsyncResult<Boolean>>() {
                    @Override
                    public void handle(AsyncResult<Boolean> event) {
                        if (event.succeeded()) {
                            if (event.result()) {
                                sendFile(req, filesystemLocation, acceptEncodingGzip);
                            } else {
                                if (filesystemLocation.endsWith(".js") ||
                                        filesystemLocation.endsWith(".map") ||
                                        filesystemLocation.endsWith(".css") ||
                                        filesystemLocation.endsWith(".less") ||
                                        filesystemLocation.endsWith(".json") ||
                                        filesystemLocation.endsWith(".png") ||
                                        filesystemLocation.endsWith(".jpg") ||
                                        filesystemLocation.endsWith(".jpeg") ||
                                        filesystemLocation.endsWith(".gif") ||
                                        filesystemLocation.endsWith(".txt") ||
                                        filesystemLocation.endsWith(".html")) {
                                    req.response().setStatusCode(404).setStatusMessage("Not Found");
                                    req.response().end();
                                    return;
                                } else {
                                    sendFile(req, indexHtmlLocation, acceptEncodingGzip);
                                }
                            }
                        } else {
                            req.response().setStatusCode(500);
                            req.response().end();
                            return;
                        }
                    }
                });
            }
            else {
                req.response().setStatusCode(500).end();
            }
        });
        server.requestHandler(req -> { router.accept(req); });
        server.listen(1080);
    }
}
