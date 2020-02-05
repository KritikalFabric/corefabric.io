package org.kritikal.fabric.net.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import org.kritikal.fabric.CoreFabric;

import java.util.Set;
import java.util.UUID;

public class SecureCFCookieCutter implements CFCookieCutter {
    public static class Credentials {
        public byte[] code_key, hash_key;
    }
    public static class SecureCFCookie extends CFCookie {
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid) {
            super(originalCookieValue, session_uuid);
        }
        protected SecureCFCookie() {
            super();
        }
    }
    public SecureCFCookie parse(String originalCookieValue) {
        try {
            UUID try_parse = UUID.fromString(originalCookieValue);
            if (null != try_parse) {
                return new SecureCFCookie(originalCookieValue, try_parse);
            }
        }
        catch (Throwable t) {
            CoreFabric.logger.warn("cookie", t);
        }
        return new SecureCFCookie();
    }
    public SecureCFCookieCutter(Credentials credentials) {
        this.credentials = credentials;
    }
    private final Credentials credentials;
    @Override
    public CFCookie cut(HttpServerRequest req) {
        String cookieName = req.isSSL() ? "corefabric" : "cf_http";
        String cookieValue = null;
        SecureCFCookie cfCookie = null;
        try {
            String cookies = req.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if (cookieName.equals(cookie.getName())) {
                    cookieValue = cookie.getValue().trim();
                    break;
                }
            }
            if ("".equals(cookieValue)) {
                cookieValue = null;
            }
            UUID session_uuid = UUID.fromString(cookieValue); // does it parse?
            cfCookie = new SecureCFCookie(cookieValue, session_uuid);
        } catch (Throwable t) {
            cfCookie = null;
        }
        if (cfCookie == null) {
            cfCookie = new SecureCFCookie();
        }

        {
            String cfcookie = cookieName + "=" + cfCookie.cookieValue() + "; Path=/";
            if (req.isSSL())
                cfcookie = cfcookie + "; Secure";

            req.response().headers().add("Set-Cookie", cfcookie);
        }
        return cfCookie;
    }

    @Override
    public CFCookie cut(ServerWebSocket webSocket) {
        String cookieValue = null;
        try {
            String cookies = webSocket.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if ("corefabric".equals(cookie.getName())) {
                    cookieValue = cookie.getValue().trim();
                    UUID session_uuid = UUID.fromString(cookieValue); // does it parse?
                    return new SecureCFCookie(cookieValue, session_uuid);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return new SecureCFCookie();
    }
}
