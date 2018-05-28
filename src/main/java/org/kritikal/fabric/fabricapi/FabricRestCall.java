package org.kritikal.fabric.fabricapi;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class FabricRestCall {
    public FabricRestCall(RoutingContext rc) {
        this.rc = rc;
        this.req = rc.request();
    }
    public final RoutingContext rc;
    public final HttpServerRequest req;
    public final String param(String paramName) {
        return this.req.getParam(paramName);
    }
    public final byte[] body() {
        return this.rc.getBody().getBytes();
    }
    private JsonObject _object = null;
    public final JsonObject object() {
        if (_object != null) return _object;
        final byte[] body = body();
        try {
            final String string = new String(body, "utf-8");
            return _object = new JsonObject(string);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
