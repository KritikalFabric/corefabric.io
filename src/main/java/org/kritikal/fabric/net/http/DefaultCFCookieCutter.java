package org.kritikal.fabric.net.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

import java.util.Set;
import java.util.UUID;

public class DefaultCFCookieCutter implements CFCookieCutter {
    @Override
    public CFCookie cut(HttpServerRequest req) {
        boolean found = false;
        boolean set = false;
        String cookieName = req.isSSL() ? "corefabric" : "cf_http";
        String corefabric = null;
        CFCookie cfCookie = null;
        try {
            String cookies = req.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if (cookieName.equals(cookie.getName())) {
                    corefabric = cookie.getValue().trim();
                    break;
                }
            }
            if ("".equals(corefabric)) {
                corefabric = null;
            }
            UUID session_uuid = UUID.fromString(corefabric); // does it parse?
            cfCookie = new CFCookie(session_uuid);
            found = true;
        } catch (Throwable t) {
            corefabric = null;
            cfCookie = null;
        }
        if (cfCookie == null) {
            cfCookie = new CFCookie();
            corefabric = cfCookie.session_uuid.toString();
        }

        {
            String cfcookie = cookieName + "=" + corefabric + "; Path=/";
            if (req.isSSL())
                cfcookie = cfcookie + "; Secure";

            req.response().headers().add("Set-Cookie", cfcookie);
            set = true;
        }
        //logger.warn("cookie-cutter\tssl=" + req.isSSL() + "\t" + req.host() + "\t" + req.remoteAddress().host() + "\t" + req.host() + "\t" + req.path() + "\tf=" + found + "\ts=" + set + "\t" + corefabric);
        return cfCookie;
    }

    @Override
    public CFCookie cut(ServerWebSocket webSocket) {
        String corefabric = null;
        try {
            String cookies = webSocket.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if ("corefabric".equals(cookie.getName())) {
                    corefabric = cookie.getValue().trim();
                    UUID session_uuid = UUID.fromString(corefabric); // does it parse?
                    return new CFCookie(session_uuid);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return new CFCookie();
    }

    @Override
    public CFCookie generate() {
        return new CFCookie();
    }
}
