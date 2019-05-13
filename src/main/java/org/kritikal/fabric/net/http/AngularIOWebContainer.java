package org.kritikal.fabric.net.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.annotations.CFApi;
import org.kritikal.fabric.annotations.CFApiBase;
import org.kritikal.fabric.annotations.CFApiMethod;
import org.kritikal.fabric.core.BuildConfig;
import org.kritikal.fabric.core.Configuration;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.net.mqtt.SyncMqttBroker;
import org.reflections.Reflections;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.concurrent.ConcurrentHashMap;

public class AngularIOWebContainer {
    final static Logger logger = LoggerFactory.getLogger(AngularIOWebContainer.class);
    static String runningJar;
    static boolean runningInsideJar;
    static {
        try {
            runningJar = new File(AngularIOWebContainer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            //if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\tCode running from:\t" + runningJar);
            runningInsideJar = runningJar.endsWith("-fat.jar");
        }
        catch (URISyntaxException use) {
            logger.warn("angular-io\tURI syntax exception");
            runningInsideJar = false;
        }
    }

    final static ConcurrentHashMap<String, AngularIOSiteInstance> map = new ConcurrentHashMap<>();
    final public static ConcurrentHashMap<String, AngularIOSiteInstance> map() { return map; }

    public static String cookieCutter(HttpServerRequest req) {
        boolean found = false;
        boolean set = false;
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
            found = true;
        } catch (Throwable t) {
            corefabric = null;
        }
        if (corefabric == null) {
            corefabric = UUID.randomUUID().toString();
            String cfcookie = "corefabric=" + corefabric + "; Path=/";
            /*
            if (req.isSSL())
                cfcookie = cfcookie + "; Secure";
            */
            req.response().headers().add("Set-Cookie", cfcookie);
            set = true;
        }

        //logger.warn("cookie-cutter\tssl=" + req.isSSL() + "\t" + req.host() + "\t" + req.path() + "\tf=" + found + "\ts=" + set + "\t" + corefabric);
        return corefabric;
    }

    public static String hostCutter(HttpServerRequest request) {
        String host = request.host();
        if (null == host || "".equals(host)) return ".";
        int i = host.indexOf(':');
        if (i >= 0) { host = host.substring(0, i); }
        return host;
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

    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final DateFormat DATE_FORMAT_RFC1123 = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);

    public static void sendFile(HttpServerRequest req, String pathToFile, boolean acceptEncodingGzip, boolean lastModified) {
        if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Pragma", "no-cache");
            req.response().headers().add("Cache-Control", "no-cache, no-store, private, must-revalidate");
        } else {
            req.response().headers().add("Cache-Control", "public, max-age=63072000"); // 2 years
        }

        if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Content-Type", "text/html; charset=utf-8");
        } else if (pathToFile.endsWith(".js")) {
            req.response().headers().add("Content-Type", "text/javascript; charset=utf-8");
        } else if (pathToFile.endsWith(".json")) {
            req.response().headers().add("Content-Type", "application/json; charset=utf-8");
        } else if (pathToFile.endsWith(".css")) {
            req.response().headers().add("Content-Type", "text/css; charset=utf-8");
        } else if (pathToFile.endsWith(".txt")) {
            req.response().headers().add("Content-Type", "text/plain; charset=utf-8");
        } else if (pathToFile.endsWith(".png")) {
            req.response().headers().add("Content-Type", "image/png");
        } else if (pathToFile.endsWith(".gif")) {
            req.response().headers().add("Content-Type", "image/gif");
        } else if (pathToFile.endsWith(".jpg") || pathToFile.endsWith(".jpeg")) {
            req.response().headers().add("Content-Type", "image/jpeg");
        } else if (pathToFile.endsWith(".tif") || pathToFile.endsWith(".tiff")) {
            req.response().headers().add("Content-Type", "image/tiff");
        } else {
            req.response().headers().add("Content-Type", "application/octet-stream");
        }

        if (acceptEncodingGzip) {
            req.response().headers().add("Content-Encoding", "gzip");
        }

        if (lastModified) {
            java.util.Date t = new java.util.Date(BuildConfig.BUILD_UNIXTIME);
            req.response().headers().add("Last-Modified", DATE_FORMAT_RFC1123.format(t));
        }

        req.response().sendFile(pathToFile + (acceptEncodingGzip ? ".gz" : ""));
    }

    public static HttpServer initialiseHttpToHttpsRedirect(Vertx vertx, String defaultHost) {
        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        Router router = initialiseRouter(vertx);
        router.get().handler(rc->{
            String host = rc.request().host();
            if (null == host || "".equals(host)) host = defaultHost;
            String path = rc.request().path();
            String query = rc.request().query();
            int x = null == host ? -1 : host.indexOf(':');
            if (x>=0) host = host.substring(0, x);
            if (null == path || "".equals(path)) path = "/";
            String redirect = "https://" + host + path + (null == query || "".equals(query) ? "" : ("?" + query));
            rc.response().setStatusCode(301).setStatusMessage("Moved Permanently");
            rc.response().headers().add("Location", redirect);
            rc.response().end();
        });
        HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        httpServer.requestHandler(req -> { router.accept(req); });
        return httpServer;
    }

    public static class SsiParams {
        public SsiParams(final Configuration cfg, final HttpServerRequest request) {
            this.cfg = cfg;
            this.request = request;
        }
        public final Configuration cfg;
        public final HttpServerRequest request;
    }

    public static HttpServer initialiseHttpServer(String namespace, String zone, Vertx vertx, Router router, Consumer<HttpServerOptions> options, BiFunction<SsiParams, String, String> ssi) {
        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        options.accept(httpServerOptions);
        HttpServer server = vertx.createHttpServer(httpServerOptions);
        server.websocketHandler(ws -> {
            String corefabric = cookieCutter(ws);
            if (corefabric==null) ws.reject();
            if (!"/mqtt".equals(ws.path())) ws.reject();
            else {
                SyncMqttBroker mqttBroker = (SyncMqttBroker)MqttBrokerVerticle.syncBroker();//hack
                SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = new SyncMqttBroker.MyMqttServerProtocol(logger, vertx, mqttBroker, ws, corefabric);
                mqttBroker.waitingForConnect.add(mqttServerProtocol);
            }
        });
        wireUpCFApi(namespace, zone, vertx, router);
        router.get().handler(rc -> {
            HttpServerRequest req = rc.request();

            final String hostname = hostCutter(req);
            final String instancekey = zone + "/" + hostname;

            ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                String site = cfg.instanceConfig.getJsonObject("instance").getString("site");
                if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\t" + site + "\t" + req.path());
                if (site != null) {
                    try {
                        AngularIOSiteInstance x = map.get(site);
                        if (x != null
                                && cfg.instanceConfig.getJsonObject("zone").getBoolean("active")
                                && cfg.instanceConfig.getJsonObject("instance").getBoolean("active")) {
                            boolean noGzip = false;
                            String file = null;
                            if (req.path().contains("..") || req.path().contains("%2e") || req.path().contains("%2E")) {
                                req.response().setStatusCode(500);
                                req.response().end();
                                return;
                            } else if (req.path().equals("/") || req.path().startsWith("/-/")) {
                                file = "index.html";
                                noGzip = true;
                            } else if (req.path().startsWith("/")) {
                                file = req.path().substring(1);
                            } else {
                                req.response().setStatusCode(404);
                                req.response().end();
                                return;
                            }

                            if (file != null) {
                                final boolean isIndexHtml = "index.html".equals(file);

                                cookieCutter(req);

                                boolean gzip = false;
                                for (Map.Entry<String, String> stringStringEntry : req.headers()) {
                                    if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                                            stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                                        gzip = true;
                                    }
                                }
                                boolean acceptEncodingGzip = gzip && !noGzip;

                                String filesystemLocation = (runningInsideJar ? x.tempdir : (x.localDirSlash)) + file;
                                vertx.fileSystem().exists(filesystemLocation + (acceptEncodingGzip ? ".gz" : ""), new Handler<AsyncResult<Boolean>>() {
                                    @Override
                                    public void handle(AsyncResult<Boolean> event) {
                                        if (event.succeeded()) {
                                            if (event.result()) {
                                                if (isIndexHtml) {
                                                    vertx.fileSystem().readFile(filesystemLocation, (ar) -> {
                                                        if (ar.failed()) {
                                                            if ("/-/not-found/".equals(req.path())) {
                                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                                req.response().end();
                                                            } else {
                                                                req.response().setStatusCode(404).setStatusMessage("Not Found");
                                                                req.response().end("<html><head><title>Server Error</title><meta http-equiv=\"refresh\" content=\"0;URL='/-/not-found/'\" /></head><body></body></html>");
                                                            }
                                                        } else {
                                                            try {
                                                                String s = new String(ar.result().getBytes(), "UTF-8");
                                                                SsiParams ssiParams = new SsiParams(cfg, req);
                                                                s = ssi.apply(ssiParams, s);
                                                                cookieCutter(req);
                                                                req.response().headers().add("Pragma", "no-cache");
                                                                req.response().headers().add("Cache-Control", "no-cache, no-store, private, must-revalidate");
                                                                req.response().headers().add("Content-Type", "text/html; charset=utf-8");
                                                                req.response().end(s);
                                                            }
                                                            catch (Throwable t) {
                                                                logger.error("angular-io\t" + site + "\t" + req.path() + "\t" + t.getMessage());
                                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                                req.response().end();
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    cookieCutter(req);
                                                    sendFile(req, filesystemLocation, acceptEncodingGzip, runningInsideJar);
                                                }
                                            } else {
                                                if ("/-/not-found/".equals(req.path())) {
                                                    req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                    req.response().end();
                                                } else {
                                                    req.response().setStatusCode(404).setStatusMessage("Not Found");
                                                    req.response().end("<html><head><title>Server Error</title><meta http-equiv=\"refresh\" content=\"0;URL='/-/not-found/'\" /></head><body></body></html>");
                                                }
                                                return;
                                            }
                                        } else {
                                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                                            req.response().end();
                                            return;
                                        }
                                    }
                                });
                            } else {
                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                req.response().end();
                            }
                        } else {
                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                            req.response().end();
                        }
                    }
                    catch (Throwable t) {
                        logger.error("angular-io\t" + site + "\t" + req.path() + "\t" + t.getMessage());
                        req.response().setStatusCode(500).setStatusMessage("Server Error");
                        req.response().end();
                    }
                } else {
                    req.response().setStatusCode(500).setStatusMessage("Server Error");
                    req.response().end();
                }
            });
        });
        server.requestHandler(req -> { router.accept(req); });
        return server;
    }

    private static void cfApi(CFApiMethod cfApiMethod, Configuration cfg, Consumer<AngularIOSiteInstance> next, Consumer<Void> fail) {
        String site = cfg.instanceConfig.getJsonObject("instance").getString("site");
        AngularIOSiteInstance x = map.get(site);
        if (x != null
                && cfg.instanceConfig.getJsonObject("zone").getBoolean("active")
                && cfg.instanceConfig.getJsonObject("instance").getBoolean("active")) {
            boolean found = false;
            for (String s : cfApiMethod.sites()) {
                if (s.equals(site)) { found = true; break; }
            }
            if (found) {
                next.accept(x);
            }
            else {
                fail.accept(null);
            }
        } else {
            fail.accept(null);
        }
    }

    private static void wireUpCFApi(String namespace, String zone, Vertx vertx, Router router) {
        final CorsOptionsHandler corsOptionsHandler = new CorsOptionsHandler();
        Reflections reflections = new Reflections(namespace);
        for (Class<?> clazz : reflections.getTypesAnnotatedWith(CFApi.class)) {
            try {
                final Constructor ctor = clazz.getConstructor(Configuration.class);
                for (final Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(CFApiMethod.class)) {
                        CFApiMethod apiMethod = method.getAnnotation(CFApiMethod.class);
                        final String url = apiMethod.url();
                        if (apiMethod.cors()) router.options(url).handler(corsOptionsHandler);
                        switch (apiMethod.type()) {
                            case JSON_GET:
                                // json handler
                                router.get(url).handler(rc -> {
                                    HttpServerRequest req = rc.request();

                                    final String hostname = hostCutter(req);
                                    final String instancekey = zone + "/" + hostname;

                                    ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                                        cfApi(apiMethod, cfg, (x)->{
                                            if (CoreFabric.ServerConfiguration.DEBUG)
                                                logger.info("angular-io\tapi\t" + req.path());
                                            try {
                                                String corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                Consumer<JsonObject> next = (r)->{
                                                    req.response().setStatusCode(200).setStatusMessage("OK");
                                                    corsOptionsHandler.applyResponseHeaders(req);
                                                    req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                                                    req.response().end(r.encode());
                                                };
                                                method.invoke(o, next);

                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.path() + "\t" + t.getMessage());
                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                req.response().end();
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.path() + "\tNot Available");
                                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                                            req.response().end();
                                        });
                                    });
                                });
                                break;

                            case JSON_POST:
                                // json handler
                                router.post(url).handler(rc -> {
                                    HttpServerRequest req = rc.request();

                                    final String hostname = hostCutter(req);
                                    final String instancekey = zone + "/" + hostname;

                                    ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                                        cfApi(apiMethod, cfg, (x)->{
                                            if (CoreFabric.ServerConfiguration.DEBUG)
                                                logger.info("angular-io\tapi\t" + req.path());
                                            try {
                                                String corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                final byte[] body = rc.getBody().getBytes();
                                                final JsonObject _object;
                                                try {
                                                    final String string = new String(body, "utf-8");
                                                    _object = new JsonObject(string);
                                                }
                                                catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                                Consumer<JsonObject> next = (r)->{
                                                    req.response().setStatusCode(200).setStatusMessage("OK");
                                                    corsOptionsHandler.applyResponseHeaders(req);
                                                    req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                                                    req.response().end(r.encode());
                                                };
                                                method.invoke(o, _object, next);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.path() + "\t" + t.getMessage());
                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                req.response().end();
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.path() + "\tNot Available");
                                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                                            req.response().end();
                                        });
                                    });
                                });
                                break;

                            case GENERIC_POST:
                                // generic get handler
                                router.post(url).handler(rc -> {
                                    HttpServerRequest req = rc.request();

                                    final String hostname = hostCutter(req);
                                    final String instancekey = zone + "/" + hostname;

                                    ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                                        cfApi(apiMethod, cfg, (x)->{
                                            if (CoreFabric.ServerConfiguration.DEBUG)
                                                logger.info("angular-io\tapi\t" + req.path());
                                            try {
                                                String corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                method.invoke(o);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.path() + "\t" + t.getMessage());
                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                req.response().end();
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.path() + "\tNot Available");
                                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                                            req.response().end();
                                        });
                                    });
                                });
                                break;

                            default:
                            case GENERIC_GET:
                                // generic get handler
                                router.get(url).handler(rc -> {
                                    HttpServerRequest req = rc.request();

                                    final String hostname = hostCutter(req);
                                    final String instancekey = zone + "/" + hostname;

                                    ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                                        cfApi(apiMethod, cfg, (x)->{
                                            if (CoreFabric.ServerConfiguration.DEBUG)
                                                logger.info("angular-io\tapi\t" + req.path());
                                            try {
                                                String corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                method.invoke(o);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.path() + "\t" + t.getMessage());
                                                req.response().setStatusCode(500).setStatusMessage("Server Error");
                                                req.response().end();
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.path() + "\tNot Available");
                                            req.response().setStatusCode(500).setStatusMessage("Server Error");
                                            req.response().end();
                                        });
                                    });
                                });
                        }
                        if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\t" + apiMethod.url() + "\t" + clazz.getSimpleName() + "\t" + method.getName());
                    }
                }

            }
            catch (ClassCastException cce) {
                logger.fatal("angular-io\t\tUnable to wireUpCFApi " + clazz.getCanonicalName() + " class cast exception.");
            }
            catch (NoSuchMethodException e1) {
                logger.fatal("angular-io\t\tUnable to wireUpCFApi " + clazz.getCanonicalName() + " no entrypoint.");
            }
        }
    }

    public static Router initialiseRouter(Vertx vertx) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setUploadsDirectory(CoreFabric.ServerConfiguration.tmp));
        return router;
    }

    public static AngularIOSiteInstance prepare(String site, String[] matchParts, String localDir) {

        UUID uuid = UUID.randomUUID();
        AngularIOSiteInstance x = new AngularIOSiteInstance();
        x.tempdir = ConfigurationManager.Shim.tmp + "/corefabric__" + uuid.toString() + "/";
        x.localDir = localDir;
        x.localDirSlash = localDir + "/";
        try {
            File file = new File(x.tempdir);
            file.mkdirs();
        }
        catch (Throwable t) {
            // ignore
        }

        if (runningInsideJar) {
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(runningJar);
                Enumeration entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    String[] parts = entry.getName().split("/");
                    if (parts.length < matchParts.length + 1) continue;
                    boolean match = true;
                    for (int i = 0; match && i < matchParts.length; ++i) {
                        if (!parts[i].equals(matchParts[i]))
                            match = false;
                    }
                    if (!match) continue;
                    if (entry.isDirectory()) continue;

                    StringBuilder newPath = new StringBuilder();
                    newPath.append(x.tempdir);
                    for (int i = matchParts.length; i < parts.length; ++i) {
                        if (i > matchParts.length) newPath.append("/");
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

        map.put(site, x);
        if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\tServing [" + site + "] from: " + (runningInsideJar ? x.tempdir : x.localDir));

        return x;
    }
}
