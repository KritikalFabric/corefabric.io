package org.kritikal.fabric.annotations;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Configuration;
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

            CoreFabric.getVertx().executeBlocking((future)->{ withConfig.accept(c); future.complete(); }, false, (result)->{ /* nothing */ });
        });
    }

    final static DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    public final static Document newDocument() {
        try {
            return documentBuilderFactory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            logger.fatal(e);
            return null;
        }
    }

    public Document buildDocument() {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.newDocument();
            Element root = document.createElement("root");
            Element item = document.createElement("item");
            root.appendChild(item);
            Element data = document.createElement("data");
            Element year = document.createElement("year");
            year.setTextContent("" + (new java.util.Date().getYear() + 1900));
            data.appendChild(year);
            root.appendChild(data);
            Element system = document.createElement("system");
            Element instance_type = document.createElement("instance_type");;
            instance_type.appendChild(document.createTextNode(cfg.instanceConfig.getJsonObject("instance").getString("instance_type")));
            system.appendChild(instance_type);
            Element short_name = document.createElement("short_name");
            short_name.appendChild(document.createTextNode(cfg.instanceConfig.getJsonObject("instance").getString("short_name")));
            system.appendChild(short_name);
            Element test_mode = document.createElement("test_mode");
            test_mode.appendChild(document.createTextNode(cfg.instanceConfig.getJsonObject("instance").getBoolean("test_mode").toString().toLowerCase()));
            system.appendChild(test_mode);
            Element title = document.createElement("title");
            title.appendChild(document.createTextNode(cfg.instanceConfig.getJsonObject("instance").getJsonObject("object").getString("title")));
            system.appendChild(title);
            root.appendChild(system);
            document.appendChild(root);
            return document;
        }
        catch (ParserConfigurationException e) {
            logger.fatal(e);
            return null;
        }
    }
}
