package org.kritikal.fabric.net.http;

import com.google.common.html.HtmlEscapers;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.annotations.*;
import org.kritikal.fabric.core.BuildConfig;
import org.kritikal.fabric.core.Configuration;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.net.mqtt.SyncMqttBroker;
import org.reflections.Reflections;
import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.concurrent.ConcurrentHashMap;

public class AngularIOWebContainer {
    final static Logger logger = LoggerFactory.getLogger(AngularIOWebContainer.class);
    static String runningJar;
    public static final boolean runningInsideJar;
    static {
        try {
            runningJar = new File(AngularIOWebContainer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            //if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\tCode running from:\t" + runningJar);
        }
        catch (URISyntaxException use) {
            logger.warn("angular-io\tURI syntax exception");
            runningJar = "";
        }
        runningInsideJar = runningJar.endsWith("-fat.jar");
    }

    final static ConcurrentHashMap<String, AngularIOSiteInstance> map = new ConcurrentHashMap<>();
    final public static ConcurrentHashMap<String, AngularIOSiteInstance> map() { return map; }

    public static void endResponse(HttpServerRequest req, String html) {
        req.response().headers().add("Content-Type", "text/html; charset=utf-8");
        byte data[] = {};
        try {
            data = html.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {

        }
        req.response().headers().add("Content-Length", "" + data.length);
        req.response().end(html);
    }

    public static void fourOhFourEndResponse(HttpServerRequest req) {
        req.response().setStatusCode(404).setStatusMessage("Not Found");
        endResponse(req, "<html><head><title>Not Found</title><meta http-equiv=\"refresh\" content=\"0;URL='/-/not-found'\" /></head><body></body></html>");
    }

    public static void serverErrorEndResponse(HttpServerRequest req, int statusCode, String statusMessage) {
        req.response().setStatusCode(statusCode).setStatusMessage(statusMessage);
        endResponse(req, "<html><head><title>Server Error</title></head><body><h1>Server Error</h1><p><strong>" + statusCode + "</strong> - " + HtmlEscapers.htmlEscaper().escape(statusMessage) + "</p></body></html>");
    }

    public static Function<Void,CFCookieCutter> cookieCutterFactory = null;
    public static CFCookieCutter newCookieCutter() {
        if (null==cookieCutterFactory) { return new DefaultCFCookieCutter(); }
        return cookieCutterFactory.apply(null);
    }

    public static CFCookie cookieCutter(HttpServerRequest req) {
        return newCookieCutter().cut(req);
    }

    public static String hostCutter(HttpServerRequest request) {
        String host = request.host();
        if (null == host || "".equals(host)) return ".";
        int i = host.indexOf(':');
        if (i >= 0) { host = host.substring(0, i); }
        return host;
    }

    public static CFCookie cookieCutter(ServerWebSocket webSocket) {
        return newCookieCutter().cut(webSocket);
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

    public static void sendFile(HttpServerRequest req, String pathToFile, boolean acceptEncodingGzip, boolean lastModified, boolean nocache) {
        if (nocache) {
            req.response().headers().add("Pragma", "no-cache");
            req.response().headers().add("Cache-Control", "no-cache, no-store, private, must-revalidate");
        } else if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Cache-Control", "cache, store, public, max-age=60");
        } else {
            req.response().headers().add("Cache-Control", "cache, store, public, max-age=63072000"); // 2 years
        }

        if (pathToFile.endsWith(".html")) {
            req.response().headers().add("Content-Type", "text/html; charset=utf-8");
        } else if (pathToFile.endsWith(".js")) {
            req.response().headers().add("Content-Type", "text/javascript; charset=utf-8");
        } else if (pathToFile.endsWith(".json")) {
            req.response().headers().add("Content-Type", "application/json"); // charset must be utf-8 by json spec
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
        public CFNoscriptRenderers.CFXmlParameters noscriptParameters = null;
    }

    final static Pattern bingUserAgent = Pattern.compile("(compatible; (bingbot|adidxbot)| BingPreview)/", Pattern.CASE_INSENSITIVE);

    public static HttpServer initialiseHttpServer(String namespace, String zone, Vertx vertx, Router router, Consumer<HttpServerOptions> options, BiFunction<SsiParams, String, String> ssi) {
        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        httpServerOptions.setPerFrameWebsocketCompressionSupported(true);
        httpServerOptions.setPerMessageWebsocketCompressionSupported(true);
        options.accept(httpServerOptions);
        HttpServer server = vertx.createHttpServer(httpServerOptions);
        server.websocketHandler(ws -> {
            CFCookie corefabric = cookieCutter(ws);
            if (corefabric.is_new) ws.reject();
            if (!"/mqtt".equals(ws.path())) ws.reject();
            else {
                SyncMqttBroker mqttBroker = (SyncMqttBroker)MqttBrokerVerticle.syncBroker();//hack
                SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = new SyncMqttBroker.MyMqttServerProtocol(logger, vertx, mqttBroker, ws, corefabric);
                mqttBroker.waitingForConnect.add(mqttServerProtocol);
            }
        });

        final CFNoscriptRenderers noscriptRenderers = wireUpCFNoscriptRenderers(namespace, zone, vertx, router, ssi);
        wireUpCFApi(namespace, zone, vertx, router, true);
        router.get().handler(rc -> {
            HttpServerRequest req = rc.request();

            final String hostname = hostCutter(req);
            final String instancekey = zone + "/" + hostname;

            ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                String site = cfg.instanceConfig.getJsonObject("instance").getString("site");
                if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() +  "\t" + req.path());
                if (site != null) {
                    try {
                        AngularIOSiteInstance x = map.get(site);
                        if (x != null
                                && cfg.instanceConfig.getJsonObject("zone").getBoolean("active")
                                && cfg.instanceConfig.getJsonObject("instance").getBoolean("active")) {
                            boolean noGzip = false;
                            String file = null;
                            if (req.path().contains("..") || req.path().contains("%2e") || req.path().contains("%2E")) {
                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                return;
                            } else if (req.path().equals("/") || req.path().startsWith("/-/") || req.path().equals("/index.html")) {
                                file = "index.html";
                                noGzip = true;
                            } else if (req.path().startsWith("/")) {
                                file = req.path().substring(1);
                            } else {
                                fourOhFourEndResponse(req);
                                return;
                            }

                            if (file != null) {
                                final boolean isIndexHtml = "index.html".equals(file);

                                final CFCookie corefabric = cookieCutter(req);

                                boolean gzip = false;
                                for (Map.Entry<String, String> stringStringEntry : req.headers()) {
                                    if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                                            stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                                        gzip = true;
                                    }
                                }
                                final boolean acceptEncodingGzip = gzip && !noGzip;
                                final boolean gzipHtml = gzip;

                                String filesystemLocation = (runningInsideJar ? x.tempdir : (x.localDirSlash)) + file;
                                vertx.fileSystem().exists(filesystemLocation + (acceptEncodingGzip ? ".gz" : ""), new Handler<AsyncResult<Boolean>>() {
                                    @Override
                                    public void handle(AsyncResult<Boolean> event) {
                                        if (event.succeeded()) {
                                            if (event.result()) {
                                                if (isIndexHtml) {
                                                    vertx.fileSystem().readFile(filesystemLocation, (ar) -> {
                                                        if (ar.failed()) {
                                                            if ("/-/not-found".equals(req.path())) {
                                                                serverErrorEndResponse(req, 504, "Gateway Timeout");
                                                            } else {
                                                                fourOhFourEndResponse(req);
                                                            }
                                                        } else {
                                                            CFNoscriptRenderers.sharedWorkerExecutor.executeBlocking((promise)->{
                                                                try {
                                                                    String s = new String(ar.result().getBytes(), "UTF-8");
                                                                    // run no-script handler, if it exists
                                                                    CFNoscriptRenderers.CFXmlParameters parameters = null;
                                                                    String noscript = null;

                                                                    for (CFNoscriptRenderers.CFXmlRenderer renderer : noscriptRenderers.array) {
                                                                        Matcher matcher = renderer.pattern.matcher(req.path());
                                                                        if (matcher.matches()) {
                                                                            parameters = new CFNoscriptRenderers.CFXmlParameters(cfg, req, rc, corefabric);
                                                                            noscript = renderer.processor.apply(parameters);
                                                                            break;
                                                                        }
                                                                    }

                                                                    if (null != noscript) {
                                                                        String userAgent = req.headers().get("user-agent");
                                                                        // some web crawlers will prefer working html to broken javascript
                                                                        if (bingUserAgent.matcher(userAgent).find()) {
                                                                            // strip 1st noscript
                                                                            // de-noscript
                                                                            int i = s.indexOf("<noscript>");
                                                                            boolean initial = true;
                                                                            do {
                                                                                int j = s.indexOf("</noscript>", i);
                                                                                if (j > i && i >= 0) {
                                                                                    if (initial) {
                                                                                        s = s.substring(0, i) + s.substring(j + 11);
                                                                                        initial = false;
                                                                                    } else {
                                                                                        int k = s.indexOf(">", i);
                                                                                        s = s.substring(0, i) + s.substring(k+1, j) + s.substring(j+11);
                                                                                    }
                                                                                    i = s.indexOf("<noscript ");
                                                                                } else {
                                                                                    break;
                                                                                }
                                                                            }
                                                                            while (i>=0);
                                                                            // strip <script></script>
                                                                            i = s.indexOf("<script");
                                                                            do {
                                                                                int j = s.indexOf("</script>", i);
                                                                                if (j > i && i >= 0) {
                                                                                    s = s.substring(0, i) + s.substring(j + 9);
                                                                                    i = s.indexOf("<script");
                                                                                } else {
                                                                                    break;
                                                                                }
                                                                            }
                                                                            while (i>=0);
                                                                            // replace <app-root></app-root> with noscript content
                                                                            i = s.indexOf("<app-root></app-root>");
                                                                            if (i >= 0) {
                                                                                s = s.substring(0, i) + noscript + s.substring(i + 21);
                                                                            }
                                                                        } else {
                                                                            int i = s.indexOf("<noscript>");
                                                                            int j = s.indexOf("</noscript>");
                                                                            if (j > i && i >= 0) {
                                                                                s = s.substring(0, i) + "<noscript>" + noscript + "</noscript>" + s.substring(j + 11);
                                                                            }
                                                                        }
                                                                    }

                                                                    SsiParams ssiParams = new SsiParams(cfg, req);
                                                                    ssiParams.noscriptParameters = parameters;
                                                                    s = ssi.apply(ssiParams, s);

                                                                    req.response().headers().add("Cache-Control", "cache, store, public, max-age=60");
                                                                    req.response().headers().add("Content-Type", "text/html; charset=utf-8");
                                                                    /* last modified = now */ {
                                                                        java.util.Date t = new java.util.Date(); // now, this page is always modified but may be cached and stored
                                                                        req.response().headers().add("Last-Modified", DATE_FORMAT_RFC1123.format(t));
                                                                    }
                                                                    byte data[] = null;
                                                                    if (gzipHtml) {
                                                                        req.response().headers().add("Content-Encoding", "gzip");
                                                                        if (!s.startsWith("\ufeff"))
                                                                            data = gzipString('\ufeff' + s);
                                                                        else
                                                                            data = gzipString(s);
                                                                    } else {
                                                                        if (!s.startsWith("\ufeff"))
                                                                            data = ("\ufeff" + s).getBytes("UTF-8");
                                                                        else
                                                                            data = s.getBytes("UTF-8");
                                                                    }
                                                                    req.response().headers().add("Content-Length", "" + data.length);
                                                                    req.response().end(Buffer.buffer(data));
                                                                    promise.complete();
                                                                }
                                                                catch (Throwable t) {
                                                                    promise.fail(t);
                                                                }
                                                            },
                                                            false,
                                                            (result)->{
                                                                if (result.failed()) {
                                                                    logger.error("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + result.cause());
                                                                    serverErrorEndResponse(req, 500, "Internal Server Error");
                                                                }
                                                            });

                                                        }
                                                    });
                                                } else {
                                                    sendFile(req, filesystemLocation, acceptEncodingGzip, runningInsideJar, false);
                                                }
                                            } else {
                                                if ("/-/not-found".equals(req.path())) {
                                                    serverErrorEndResponse(req, 504, "Gateway Timeout");
                                                } else {
                                                    fourOhFourEndResponse(req);
                                                }
                                                return;
                                            }
                                        } else {
                                            // file does not exist
                                            fourOhFourEndResponse(req);
                                            return;
                                        }
                                    }
                                });
                            } else {
                                serverErrorEndResponse(req, 500, "Internal Server Error");
                            }
                        } else {
                            serverErrorEndResponse(req, 500, "Internal Server Error");
                        }
                    }
                    catch (Throwable t) {
                        logger.error("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                        serverErrorEndResponse(req, 500, "Internal Server Error");
                    }
                } else {
                    serverErrorEndResponse(req, 500, "Internal Server Error");
                }
            });
        });
        server.requestHandler(req -> { router.accept(req); });
        return server;
    }

    public static HttpServer initialiseHttpServerStaticRender(String namespace, String zone, Vertx vertx, Router router, Consumer<HttpServerOptions> options, BiFunction<SsiParams, String, String> ssi) {
        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setSoLinger(0);
        httpServerOptions.setTcpKeepAlive(true);
        httpServerOptions.setHandle100ContinueAutomatically(true);
        httpServerOptions.setPerFrameWebsocketCompressionSupported(true);
        httpServerOptions.setPerMessageWebsocketCompressionSupported(true);
        options.accept(httpServerOptions);
        HttpServer server = vertx.createHttpServer(httpServerOptions);

        final CFNoscriptRenderers noscriptRenderers = wireUpCFNoscriptRenderers(namespace, zone, vertx, router, ssi);
        wireUpCFApi(namespace, zone, vertx, router, false);
        router.get().handler(rc -> {
            HttpServerRequest req = rc.request();

            final String hostname = hostCutter(req);
            final String instancekey = zone + "/" + hostname;

            ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {
                String site = cfg.instanceConfig.getJsonObject("instance").getString("site");
                if (CoreFabric.ServerConfiguration.DEBUG) logger.info("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path());
                if (site != null) {
                    try {
                        AngularIOSiteInstance x = map.get(site);
                        if (x != null
                                && cfg.instanceConfig.getJsonObject("zone").getBoolean("active")
                                && cfg.instanceConfig.getJsonObject("instance").getBoolean("active")) {
                            boolean noGzip = false;
                            String file = null;
                            if (req.path().contains("..") || req.path().contains("%2e") || req.path().contains("%2E")) {
                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                return;
                            } else if (req.path().equals("/") || req.path().startsWith("/-/") || req.path().equals("/index.html")) {
                                file = "index.html";
                                noGzip = true;
                            } else if (req.path().startsWith("/")) {
                                file = req.path().substring(1);
                            } else {
                                fourOhFourEndResponse(req);
                                return;
                            }

                            if (file != null) {
                                final boolean isIndexHtml = "index.html".equals(file);

                                final CFCookie corefabric = cookieCutter(req);

                                boolean gzip = false;
                                for (Map.Entry<String, String> stringStringEntry : req.headers()) {
                                    if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                                            stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                                        gzip = true;
                                    }
                                }
                                final boolean acceptEncodingGzip = gzip && !noGzip;
                                final boolean gzipHtml = gzip;

                                String filesystemLocation = (runningInsideJar ? x.tempdir : (x.localDirSlash)) + file;
                                vertx.fileSystem().exists(filesystemLocation + (acceptEncodingGzip ? ".gz" : ""), new Handler<AsyncResult<Boolean>>() {
                                    @Override
                                    public void handle(AsyncResult<Boolean> event) {
                                        if (event.succeeded()) {
                                            if (event.result()) {
                                                if (isIndexHtml) {
                                                    vertx.fileSystem().readFile(filesystemLocation, (ar) -> {
                                                        if (ar.failed()) {
                                                            if ("/-/not-found".equals(req.path())) {
                                                                serverErrorEndResponse(req, 504, "Gateway Timeout");
                                                            } else {
                                                                fourOhFourEndResponse(req);
                                                            }
                                                        } else {
                                                            CFNoscriptRenderers.sharedWorkerExecutor.executeBlocking((promise)->{
                                                                        try {
                                                                            String s = new String(ar.result().getBytes(), "UTF-8");
                                                                            // run no-script handler, if it exists
                                                                            CFNoscriptRenderers.CFXmlParameters parameters = null;
                                                                            String noscript = null;

                                                                            for (CFNoscriptRenderers.CFXmlRenderer renderer : noscriptRenderers.array) {
                                                                                Matcher matcher = renderer.pattern.matcher(req.path());
                                                                                if (matcher.matches()) {
                                                                                    parameters = new CFNoscriptRenderers.CFXmlParameters(cfg, req, rc, corefabric);
                                                                                    noscript = renderer.processor.apply(parameters);
                                                                                    break;
                                                                                }
                                                                            }

                                                                            if (null == noscript) {
                                                                                fourOhFourEndResponse(req);
                                                                                promise.complete();
                                                                                return;
                                                                            }

                                                                            if (null != noscript) {
                                                                                // some web crawlers will prefer working html to broken javascript
                                                                                if (true) {
                                                                                    // strip 1st noscript
                                                                                    // de-noscript
                                                                                    int i = s.indexOf("<noscript>");
                                                                                    boolean initial = true;
                                                                                    do {
                                                                                        int j = s.indexOf("</noscript>", i);
                                                                                        if (j > i && i >= 0) {
                                                                                            if (initial) {
                                                                                                s = s.substring(0, i) + s.substring(j + 11);
                                                                                                initial = false;
                                                                                            } else {
                                                                                                int k = s.indexOf(">", i);
                                                                                                s = s.substring(0, i) + s.substring(k+1, j) + s.substring(j+11);
                                                                                            }
                                                                                            i = s.indexOf("<noscript ");
                                                                                        } else {
                                                                                            break;
                                                                                        }
                                                                                    }
                                                                                    while (i>=0);
                                                                                    // strip <script></script>
                                                                                    i = s.indexOf("<script");
                                                                                    do {
                                                                                        int j = s.indexOf("</script>", i);
                                                                                        if (j > i && i >= 0) {
                                                                                            s = s.substring(0, i) + s.substring(j + 9);
                                                                                            i = s.indexOf("<script");
                                                                                        } else {
                                                                                            break;
                                                                                        }
                                                                                    }
                                                                                    while (i>=0);
                                                                                    // replace <app-root></app-root> with noscript content
                                                                                    i = s.indexOf("<app-root></app-root>");
                                                                                    if (i >= 0) {
                                                                                        s = s.substring(0, i) + noscript + s.substring(i + 21);
                                                                                    }
                                                                                } else {
                                                                                    int i = s.indexOf("<noscript>");
                                                                                    int j = s.indexOf("</noscript>");
                                                                                    if (j > i && i >= 0) {
                                                                                        s = s.substring(0, i) + "<noscript>" + noscript + "</noscript>" + s.substring(j + 11);
                                                                                    }
                                                                                }
                                                                            }

                                                                            SsiParams ssiParams = new SsiParams(cfg, req);
                                                                            ssiParams.noscriptParameters = parameters;
                                                                            s = ssi.apply(ssiParams, s);

                                                                            req.response().headers().add("Cache-Control", "cache, store, public, max-age=60");
                                                                            req.response().headers().add("Content-Type", "text/html; charset=utf-8");
                                                                            /* last modified = now */ {
                                                                                java.util.Date t = new java.util.Date(); // now, this page is always modified but may be cached and stored
                                                                                req.response().headers().add("Last-Modified", DATE_FORMAT_RFC1123.format(t));
                                                                            }
                                                                            byte data[] = null;
                                                                            if (gzipHtml) {
                                                                                req.response().headers().add("Content-Encoding", "gzip");
                                                                                if (!s.startsWith("\ufeff"))
                                                                                    data = gzipString('\ufeff' + s);
                                                                                else
                                                                                    data = gzipString(s);
                                                                            } else {
                                                                                if (!s.startsWith("\ufeff"))
                                                                                    data = ("\ufeff" + s).getBytes("UTF-8");
                                                                                else
                                                                                    data = s.getBytes("UTF-8");
                                                                            }
                                                                            req.response().headers().add("Content-Length", "" + data.length);
                                                                            req.response().end(Buffer.buffer(data));
                                                                            promise.complete();
                                                                        }
                                                                        catch (Throwable t) {
                                                                            promise.fail(t);
                                                                        }
                                                                    },
                                                                    false,
                                                                    (result)->{
                                                                        if (result.failed()) {
                                                                            logger.error("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + result.cause());
                                                                            fourOhFourEndResponse(req);
                                                                        }
                                                                    });

                                                        }
                                                    });
                                                } else {
                                                    sendFile(req, filesystemLocation, acceptEncodingGzip, runningInsideJar, false);
                                                }
                                            } else {
                                                if ("/-/not-found".equals(req.path())) {
                                                    serverErrorEndResponse(req, 504, "Gateway Timeout");
                                                } else {
                                                    fourOhFourEndResponse(req);
                                                }
                                                return;
                                            }
                                        } else {
                                            serverErrorEndResponse(req, 500, "Internal Server Error");
                                        }
                                    }
                                });
                            } else {
                                serverErrorEndResponse(req, 500, "Internal Server Error");
                            }
                        } else {
                            serverErrorEndResponse(req, 500, "Internal Server Error");
                        }
                    }
                    catch (Throwable t) {
                        logger.error("angular-io\t" + site + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                        serverErrorEndResponse(req, 500, "Internal Server Error");
                    }
                } else {
                    serverErrorEndResponse(req, 500, "Internal Server Error");
                }
            });
        });
        server.requestHandler(req -> { router.accept(req); });
        return server;
    }

    private static String cfRender(CFRenderMethod cfRenderMethod, Configuration cfg, Function<AngularIOSiteInstance, String> process, Function<Void, String> fail) {
        String site = cfg.instanceConfig.getJsonObject("instance").getString("site");
        AngularIOSiteInstance x = map.get(site);
        if (x != null
                && cfg.instanceConfig.getJsonObject("zone").getBoolean("active")
                && cfg.instanceConfig.getJsonObject("instance").getBoolean("active")) {
            boolean found = false;
            for (String s : cfRenderMethod.sites()) {
                if (s.equals(site)) { found = true; break; }
            }
            if (found) {
                return process.apply(x);
            }
            else {
                return fail.apply(null);
            }
        } else {
            return fail.apply(null);
        }
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

    private static final void gzipJson(final HttpServerRequest req, final JsonObject r, final CorsOptionsHandler corsOptionsHandler, boolean nocache, CFCookie cookie) {
        req.response().setStatusCode(200).setStatusMessage("OK");
        corsOptionsHandler.applyResponseHeaders(req, !nocache);
        req.response().headers().add("Content-Type", "application/json");

        if (null != cookie) {
            newCookieCutter().apply(req, cookie);
        }

        boolean gzip = false;
        for (Map.Entry<String, String> stringStringEntry : req.headers()) {
            if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                    stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                gzip = true;
            }
        }
        byte data[] = null;
        boolean gzipped = false;
        if (gzip) {
            try {
                data = gzipString(r.encode());
                gzipped = true;
            } catch (Exception e) {
                data = null;
            }
        }
        if (null == data) {
            try {
                data = r.encode().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        if (gzipped) req.response().headers().add("Content-Encoding", "gzip");
        req.response().headers().add("Content-Length", "" + data.length);
        req.response().end(Buffer.buffer(data));
    }

    private static final byte[] gzipString(final String str) throws Exception {
            if (str == null || str.length() == 0) {
                return null;
            }
            ByteArrayOutputStream obj=new ByteArrayOutputStream();
            try {
                GZIPOutputStream gzip = new GZIPOutputStream(obj);
                try {
                    gzip.write(str.getBytes("UTF-8"));
                }
                finally {
                    gzip.close();
                    obj.flush();
                }
                return obj.toByteArray();
            }
            finally {
                obj.close();
            }
    }

    private static CFNoscriptRenderers wireUpCFNoscriptRenderers(String namespace, String zone, Vertx vertx, Router router, BiFunction<SsiParams, String, String> ssi) {
        final CFNoscriptRenderers result = new CFNoscriptRenderers();
        Reflections reflections = new Reflections(namespace);
        for (Class<?> clazz : reflections.getTypesAnnotatedWith(CFApi.class)) {
            try {
                final Constructor ctor = clazz.getConstructor(Configuration.class);
                for (final Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(CFRenderMethod.class)) {
                        CFRenderMethod renderMethod = method.getAnnotation(CFRenderMethod.class);

                        final Pattern pattern = Pattern.compile(renderMethod.regex(), Pattern.CASE_INSENSITIVE);
                        final Function<CFNoscriptRenderers.CFXmlParameters, String> processor = (params) -> cfRender(renderMethod, params.cfg, (x)->{
                            String filesystemLocation = (runningInsideJar ? x.tempdir : (x.localDirSlash)) + renderMethod.template();
                            final Transformer xsl = result.get(filesystemLocation);
                            try {
                                Object o = ctor.newInstance(params.cfg);
                                params.api = (CFApiBase)o;
                                ((CFApiBase)o).setCookie(params.corefabric);
                                ((CFApiBase)o).setRoutingContext(params.rc);
                                Function<Document, String> fn = (doc)->{
                                    DOMResult output = new DOMResult();
                                    try {
                                        xsl.transform(new DOMSource(doc), output);
                                        if (renderMethod.type()== CFRenderMethod.TYPE.NOSCRIPT)
                                            return CFNoscriptRenderers.getBodyTagAsString(output.getNode().getFirstChild().getFirstChild()); // html -> body
                                        else if (renderMethod.type()== CFRenderMethod.TYPE.AMP)
                                            return CFNoscriptRenderers.nodeToString(output.getNode()); // html
                                        else {
                                            return null;
                                        }
                                    } catch (TransformerException e) {
                                        logger.error("angular-io\tapi\t" + params.req.path() + "\t" + e.getMessage());
                                        return null;
                                    }
                                };

                                return (String)method.invoke(o, fn);
                            } catch (Throwable t) {
                                logger.error("angular-io\tapi\t" + params.req.path() + "\t" + t.getMessage());
                                return null;
                            }
                        }, (fail)->{
                            return null;
                        });
                        if (renderMethod.type()== CFRenderMethod.TYPE.NOSCRIPT) {
                            result.add(pattern, processor);
                        } else {
                            router.getWithRegex(renderMethod.regex()).handler(rc -> {
                                HttpServerRequest req = rc.request();

                                final String hostname = hostCutter(req);
                                final String instancekey = zone + "/" + hostname;

                                ConfigurationManager.getConfigurationAsync(vertx, instancekey, cfg -> {

                                    final CFCookie corefabric = cookieCutter(req);
                                    CFNoscriptRenderers.CFXmlParameters params = new CFNoscriptRenderers.CFXmlParameters(cfg, req, rc, corefabric);

                                    CFNoscriptRenderers.sharedWorkerExecutor.executeBlocking(promise1 -> {
                                        try {
                                            promise1.complete(processor.apply(params));
                                        }
                                        catch (Exception e) {
                                            logger.warn(e);
                                            promise1.fail(e);
                                        }
                                    }, false, result1->{

                                        if (result1.failed() || null==result1.result()) {
                                            fourOhFourEndResponse(req);
                                        } else {
                                            boolean gzip = false;
                                            for (Map.Entry<String, String> stringStringEntry : req.headers()) {
                                                if (stringStringEntry.getKey().toLowerCase().equals("accept-encoding") &&
                                                        stringStringEntry.getValue().toLowerCase().contains("gzip")) {
                                                    gzip = true;
                                                }
                                            }
                                            SsiParams ssiParams = new SsiParams(cfg, req);
                                            ssiParams.noscriptParameters = params;
                                            String html = ssi.apply(ssiParams, (String) result1.result());

                                            req.response().headers().add("Cache-Control", "cache, store, public, max-age: 3600");
                                            req.response().headers().add("Content-Type", "text/html; charset=utf-8");
                                            /* last modified = now */
                                            {
                                                java.util.Date t = new java.util.Date(); // now, this page is always modified but may be cached and stored
                                                req.response().headers().add("Last-Modified", DATE_FORMAT_RFC1123.format(t));
                                            }
                                            try {
                                                byte data[] = null;
                                                if (gzip) {
                                                    req.response().headers().add("Content-Encoding", "gzip");
                                                    if (!html.startsWith("\ufeff"))
                                                        data = gzipString('\ufeff' + html);
                                                    else
                                                        data = gzipString(html);
                                                } else {
                                                    if (!html.startsWith("\ufeff"))
                                                        data = ("\ufeff" + html).getBytes("UTF-8");
                                                    else
                                                        data = html.getBytes("UTF-8");
                                                }
                                                req.response().headers().add("Content-Length", "" + data.length);
                                                req.response().end(Buffer.buffer(data));

                                            } catch (Exception e) {
                                                logger.warn(e);
                                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                            }
                                        }
                                    });

                                });
                            });
                        }

                        if (CoreFabric.ServerConfiguration.DEBUG)
                            logger.info("angular-io\t" + renderMethod.regex() + "\t" + renderMethod.type().name() + "\t" + renderMethod.template() + "\t" + clazz.getSimpleName() + "\t" + method.getName());
                    }
                }
            }
            catch (ClassCastException cce) {
                logger.fatal("angular-io\t\tUnable to wireUpCFNoscriptRenderers " + clazz.getCanonicalName() + " class cast exception.");
            }
            catch (NoSuchMethodException e1) {
                logger.fatal("angular-io\t\tUnable to wireUpCFNoscriptRenderers " + clazz.getCanonicalName() + " no entrypoint.");
            }
            catch (Throwable t) {
                logger.fatal("angular-io\t\tUnable to wireUpCFNoscriptRenderers " + clazz.getCanonicalName(), t);
            }
        }
        return result;
    }
    private static void wireUpCFApi(String namespace, String zone, Vertx vertx, Router router, boolean isHttps) {
        final CorsOptionsHandler corsOptionsHandler = new CorsOptionsHandler();
        Reflections reflections = new Reflections(namespace);
        for (Class<?> clazz : reflections.getTypesAnnotatedWith(CFApi.class)) {
            try {
                final Constructor ctor = clazz.getConstructor(Configuration.class);
                for (final Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(CFApiMethod.class)) {
                        CFApiMethod apiMethod = method.getAnnotation(CFApiMethod.class);
                        if (isHttps) {
                            if (!apiMethod.https()) continue;
                        } else {
                            if (!apiMethod.http()) continue;
                        }
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
                                                logger.info("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path());
                                            try {
                                                final CFCookie corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                Consumer<JsonObject> next = (r)->{
                                                    gzipJson(req, r, corsOptionsHandler, apiMethod.nocache(), corefabric);
                                                };
                                                method.invoke(o, next);

                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\tNot Available");
                                            serverErrorEndResponse(req, 500, "Internal Server Error");
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
                                                logger.info("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path());
                                            try {
                                                final CFCookie corefabric = cookieCutter(req);
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
                                                    gzipJson(req, r, corsOptionsHandler, apiMethod.nocache(), corefabric);
                                                };
                                                method.invoke(o, _object, next);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\tNot Available");
                                            serverErrorEndResponse(req, 500, "Internal Server Error");
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
                                                logger.info("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path());
                                            try {
                                                final CFCookie corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                method.invoke(o);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                                                serverErrorEndResponse(req,  500, "Internal Server Error");
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\tNot Available");
                                            serverErrorEndResponse(req, 500, "Internal Server Error");
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
                                                logger.info("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path());
                                            try {
                                                final CFCookie corefabric = cookieCutter(req);
                                                Object o = ctor.newInstance(cfg);
                                                ((CFApiBase)o).setCookie(corefabric);
                                                ((CFApiBase)o).setRoutingContext(rc);
                                                method.invoke(o);
                                            } catch (Throwable t) {
                                                logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\t" + t.getMessage());
                                                serverErrorEndResponse(req, 500, "Internal Server Error");
                                            }
                                        }, (fail1)->{
                                            logger.error("angular-io\tapi\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\tNot Available");
                                            serverErrorEndResponse(req, 500, "Internal Server Error");
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
