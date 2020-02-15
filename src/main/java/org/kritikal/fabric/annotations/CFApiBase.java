package org.kritikal.fabric.annotations;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Configuration;
import org.kritikal.fabric.net.http.CFCookie;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
    private CFCookie corefabric = null;
    public void setCookie(CFCookie s) {
        corefabric = s;
    }
    public CFCookie getCookie() {
        //CoreFabric.logger.warn(request().host() + request().path() + "\tget\t" + corefabric.session_uuid.toString());
        return corefabric;
    }
    JsonObject schemaOrgMetaData = null;
    public void setSchemaOrgMetaData(JsonObject object) {
        this.schemaOrgMetaData = object;
    }
    public boolean hasSchemaOrgMetaData() {
        return null != this.schemaOrgMetaData;
    }
    public JsonObject getSchemaOrgMetaData() {
        return this.schemaOrgMetaData;
    }

    public void proceedIfCookie(Consumer<UUID> ifCookie, Consumer<Void> ifNoCookie) {
        boolean preAuthFailure = false;
        CFCookie corefabric = getCookie();
        if (corefabric.is_new) {
            ifNoCookie.accept(null);
        } else {
            ifCookie.accept(corefabric.session_uuid);
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

    public final static DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    public final static Document newDocument() {
        try {
            return documentBuilderFactory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            logger.fatal(e);
            return null;
        }
    }

    public abstract Document buildDocument();
}
