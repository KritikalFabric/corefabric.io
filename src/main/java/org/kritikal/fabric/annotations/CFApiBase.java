package org.kritikal.fabric.annotations;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
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
}
