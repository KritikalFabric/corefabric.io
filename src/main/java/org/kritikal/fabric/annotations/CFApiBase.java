package org.kritikal.fabric.annotations;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Configuration;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public abstract class CFApiBase {
    public final Configuration cfg;
    public CFApiBase(final Configuration cfg) {
        this.cfg = cfg;
    }
    final static Pattern reQuery = Pattern.compile("&(?![a-z0-9]+;)", Pattern.CASE_INSENSITIVE);
    final static Logger logger = LoggerFactory.getLogger(CFApiBase.class);
    public HashMap<String, String> splitQuery(HttpServerRequest req) {
        HashMap<String, String> args = new HashMap<>();
        for (String part : reQuery.split(req.query())) {
            int i = part.indexOf('=');
            if (i >= 0) {
                String k = part.substring(0, i);
                String v = part.substring(i + 1);
                args.put(k, v);
                //logger.warn("args " + k + " " + v);
            } else {
                args.put(part, "");
            }
        }
        return args;
    }
    String corefabric = null;
    public void setCookie(String s) {
        corefabric = s;
    }
    public String getCookie() {
        return corefabric;
    }

    public void proceedIfCookie(Consumer<UUID> ifCookie, Consumer<Void> ifNoCookie) {
        boolean preAuthFailure = false;
        String corefabric = getCookie();
        UUID cfuuid = null;
        if (null!=corefabric) {
            try {
                cfuuid = UUID.fromString(corefabric);
            }
            catch (Exception e) {
                preAuthFailure = true;
            }
        } else {
            preAuthFailure = true;
        }

        if (preAuthFailure || null == cfuuid) {
            logger.warn("Pre-auth failure"); //FIXME
            ifNoCookie.accept(null);
        } else {
            final UUID x = cfuuid;
            ifCookie.accept(x);
        }
    }

    private RoutingContext r = null;
    public void setRoutingContext(RoutingContext ctx) {
        this.r = ctx;
    }
    public RoutingContext ctx() { return this.r; }
    public HttpServerRequest request() {
        return this.r.request();
    }
    public void withConfigFor(final String short_name, Consumer<Configuration> withConfig) {
        final String zone = "street-stall.space";

        // Fake a config ping for the trader
        final JsonObject dbQuery = new JsonObject();
        dbQuery.put("action", "by_short_name");
        dbQuery.put("zone", zone);
        dbQuery.put("short_name", short_name);

        CoreFabric.getVertx().eventBus().send("corefabric.app-config-db." + zone, dbQuery, (ar) -> {
            JsonObject configuration = null;
            if (ar.succeeded()) {
                configuration = (JsonObject) ar.result().body();
            } else {
                configuration = new JsonObject();
            }

            final Configuration c = new Configuration(short_name); // lie about instance name
            c.applyInstanceConfig(configuration);
            // we ignore local config

            withConfig.accept(c);
        });
    }
}
