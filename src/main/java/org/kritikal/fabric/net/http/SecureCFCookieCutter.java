package org.kritikal.fabric.net.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.CFLogEncrypt;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Set;
import java.util.UUID;

public class SecureCFCookieCutter implements CFCookieCutter {
    public static class Credentials {
        public byte[] code_key, hash_key;
    }
    public class SecureCFCookie extends CFCookie {
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid) {
            super(originalCookieValue, session_uuid);
        }
        protected SecureCFCookie() {
            super(null, UUID.randomUUID());
        }

        @Override
        public String cookieValue() {
            String originalValue = super.cookieValue();
            if (null==originalValue) {
                return provider.encrypt(session_uuid.toString());
            }
            return originalValue;
        }
    }
    public class SecureCookieEncrypt implements CFLogEncrypt {
        @Override
        public String encrypt(String plaintext) {
            try {
                Cipher cipher = Cipher.getInstance("AES", new BouncyCastleProvider());
                SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
                byte[] encryptedValue = Base64.encodeBase64(encrypted);
                return "1." + new String(encryptedValue);
            }
            catch (Throwable t) {
                CoreFabric.logger.fatal("log-encrypt", t);
                throw new RuntimeException(t);
            }
        }
        @Override
        public String decrypt(String ciphertext) {
            try {
                int i = ciphertext.indexOf('.');
                if (i == -1) throw new RuntimeException();
                switch (ciphertext.substring(0, i)) {
                    case "1":
                        Cipher cipher = Cipher.getInstance("AES", new BouncyCastleProvider());
                        SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        byte[] decodedBytes = Base64.decodeBase64(ciphertext.substring(2).getBytes("UTF-8"));
                        byte[] original = cipher.doFinal(decodedBytes);
                        return new String(original, "UTF-8");
                    default:
                        throw new RuntimeException();
                }
            }
            catch (Throwable t) {
                CoreFabric.logger.fatal("log-encrypt", t);
                throw new RuntimeException(t);
            }
        }
    }
    public SecureCFCookie parse(String originalCookieValue) {
        try {
            UUID try_parse = UUID.fromString(provider.decrypt(originalCookieValue));
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
        this.provider = new SecureCookieEncrypt();
    }
    private final Credentials credentials;
    private final CFLogEncrypt provider;
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
            cfCookie = parse(cookieValue);
        } catch (Throwable t) {
            cfCookie = null;
        }
        if (cfCookie == null) {
            cfCookie = new SecureCFCookie();
        }

        String cfcookie = cookieName + "=" + cfCookie.cookieValue() + "; Path=/";
        if (req.isSSL())
            cfcookie = cfcookie + "; Secure";

        req.response().headers().add("Set-Cookie", cfcookie);
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
                    return parse(cookieValue);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return new SecureCFCookie();
    }
}
