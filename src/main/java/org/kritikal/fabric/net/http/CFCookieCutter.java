package org.kritikal.fabric.net.http;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

public interface CFCookieCutter {
    CFCookie parse(String originalCookieValue);
    CFCookie cut(HttpServerRequest req);
    CFCookie cut(ServerWebSocket webSocket);
}