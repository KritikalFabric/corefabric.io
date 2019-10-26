package org.kritikal.fabric.net.http;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.kritikal.fabric.fabricapi.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FabricApiConnector {

    public static void connectRestApi(Class clazz, Router router, CorsOptionsHandler corsOptionsHandler) {
        Annotation a = clazz.getAnnotation(FabricRestApi.class);
        if (a == null) return;

        FabricRestApi restApi = (FabricRestApi)a;
        try {
            final Object o = clazz.getConstructor(new Class[]{}).newInstance();
            if (restApi.worker()) {
                connectRestApiWorker(restApi, o, clazz, router, corsOptionsHandler);
            } else {
                connectRestApiAsync(restApi, o, clazz, router, corsOptionsHandler);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void connectRestApiWorker(FabricRestApi restApi, final Object o, Class clazz, Router router, CorsOptionsHandler corsOptionsHandler) {
        Set<String> paths = new HashSet<String>();
        for (final Method man : clazz.getMethods()) {
            final FabricHttpGet httpGet = man.getAnnotation(FabricHttpGet.class);
            if (null != httpGet) {
                String url = restApi.url() + httpGet.url();
                paths.add(url);
                router.get(url).blockingHandler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        final Object result = man.invoke(o, call);
                        req.response().headers().add("Access-Control-Allow-Credentials", "true");
                        String optionsOrigin = req.headers().get("Origin");
                        if (optionsOrigin == null) optionsOrigin = "*";
                        req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
                        if (result instanceof byte[]) {
                            req.response().setStatusCode(200).setStatusMessage("OK");
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/octet-stream");
                            req.response().end(Buffer.buffer((byte[])result));
                        } else if (result instanceof JsonArray) {
                            req.response().setStatusCode(200).setStatusMessage("OK");
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                            req.response().end(Buffer.buffer(((JsonArray)result).encode().getBytes("utf-8")));

                        } else if (result instanceof JsonObject) {
                            req.response().setStatusCode(200).setStatusMessage("OK");
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                            req.response().end(Buffer.buffer(((JsonObject)result).encode().getBytes("utf-8")));
                        } else {
                            req.response().setStatusCode(501).end();
                        }
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpPost httpPost = man.getAnnotation(FabricHttpPost.class);
            if (null != httpPost) {
                String url = restApi.url() + httpPost.url();
                paths.add(url);
                router.post(url).blockingHandler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpPut httpPut = man.getAnnotation(FabricHttpPut.class);
            if (null != httpPut) {
                String url = restApi.url() + httpPut.url();
                paths.add(url);
                router.put(url).blockingHandler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpDelete httpDelete = man.getAnnotation(FabricHttpDelete.class);
            if (null != httpDelete) {
                String url = restApi.url() + httpDelete.url();
                paths.add(url);
                router.delete(url).blockingHandler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            for (String path : paths) {
                router.options(path).handler(corsOptionsHandler);
            }
        }
    }

    private static void connectRestApiAsync(FabricRestApi restApi, final Object o, Class clazz, Router router, CorsOptionsHandler corsOptionsHandler) {
        Set<String> paths = new HashSet<String>();
        for (final Method man : clazz.getMethods()) {
            final FabricHttpGet httpGet = man.getAnnotation(FabricHttpGet.class);
            if (null != httpGet) {
                String url = restApi.url() + httpGet.url();
                paths.add(url);
                router.get(url).handler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        final Object result = man.invoke(o, call);
                        req.response().headers().add("Access-Control-Allow-Credentials", "true");
                        String optionsOrigin = req.headers().get("Origin");
                        if (optionsOrigin == null) optionsOrigin = "*";
                        req.response().headers().add("Access-Control-Allow-Origin", optionsOrigin);
                        if (result instanceof byte[]) {
                            req.response().setStatusCode(200);
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/octet-stream");
                            req.response().end(Buffer.buffer((byte[])result));
                        } else if (result instanceof JsonArray) {
                            req.response().setStatusCode(200);
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                            req.response().end(Buffer.buffer(((JsonArray)result).encode().getBytes("utf-8")));

                        } else if (result instanceof JsonObject) {
                            req.response().setStatusCode(200);
                            req.response().headers().add("Cache-Control", "private, max-age=5");
                            req.response().headers().add("Content-Type", "application/json; charset=utf-8");
                            req.response().end(Buffer.buffer(((JsonObject)result).encode().getBytes("utf-8")));
                        } else {
                            req.response().setStatusCode(501).end();
                        }
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpPost httpPost = man.getAnnotation(FabricHttpPost.class);
            if (null != httpPost) {
                String url = restApi.url() + httpPost.url();
                paths.add(url);
                router.post(url).handler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpPut httpPut = man.getAnnotation(FabricHttpPut.class);
            if (null != httpPut) {
                String url = restApi.url() + httpPut.url();
                paths.add(url);
                router.put(url).handler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            final FabricHttpDelete httpDelete = man.getAnnotation(FabricHttpDelete.class);
            if (null != httpDelete) {
                String url = restApi.url() + httpDelete.url();
                paths.add(url);
                router.delete(url).handler((rc) -> {
                    final FabricRestCall call = new FabricRestCall(rc);
                    final HttpServerRequest req = rc.request();
                    try {
                        man.invoke(o, call);
                        req.response().end();
                    }
                    catch (Exception e) {
                        req.response().setStatusCode(599).end();
                        throw new RuntimeException(e);
                    }
                });
            }

            for (String path : paths) {
                router.options(path).handler(corsOptionsHandler);
            }
        }
    }
}
