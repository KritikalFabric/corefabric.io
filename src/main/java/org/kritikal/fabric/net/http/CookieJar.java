package org.kritikal.fabric.net.http;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by ben on 4/27/16.
 */
public class CookieJar {

    final HashMap<String, String> cookies = new HashMap<>();

    public void fill(HttpClientResponse response) {
        for (Map.Entry<String, String> kv : response.headers()) {
            if ("Set-Cookie".equals(kv.getKey())) {
                bakeCookie(kv.getValue());
            }
        }
    }

    public void fill(HttpServerResponse response) {
        for (Map.Entry<String, String> kv : response.headers()) {
            if ("Set-Cookie".equals(kv.getKey())) {
                bakeCookie(kv.getValue());
            }
        }
    }

    public CookieJar() { }

    public CookieJar(HttpServerRequest request) {
        for (Map.Entry<String, String> kv : request.headers()) {
            if ("Cookie".equals(kv.getKey())) {
                bakeCookie(kv.getValue());
            }
        }
    }

    final Pattern cookieCutter = Pattern.compile(";\\s*");

    final void bakeCookie(final String cookieHeaderContents) {
        for (String value : cookieCutter.split(cookieHeaderContents)) {
            int j = value.indexOf('=');
            if (j >= 0) {
                String name = value.substring(0, j);
                value = value.substring(j + 1);
                int i = value.indexOf(';');
                if (i >= 0) value = value.substring(0, i);
                if (value.startsWith("\"") || value.startsWith("'"))
                    value = value.substring(1, value.length() - 1); // FIXME
                cookies.put(name.trim(), value.trim());
            }
        }
    }

    public String get(String cookie) { return cookies.get(cookie); }

}
