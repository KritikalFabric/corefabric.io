package org.kritikal.fabric.net.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.CFLogEncrypt;
import org.kritikal.fabric.net.ThreadLocalSecurity;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.kritikal.fabric.net.http.DefaultCFCookieCutter.formatSetCookie;

public class SecureCFCookieCutter implements CFCookieCutter {
    public static class Credentials {
        public byte[] code_key, hash_key;
    }
    public class SecureCFCookie extends CFCookie {
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid) {
            super(originalCookieValue, session_uuid);
        }
        public SecureCFCookie() {
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
                StringBuilder sb = new StringBuilder();
                sb.append("2."); // format
                byte[] entropy = ThreadLocalSecurity.secureRandom.get().getSeed(64);
                byte[] plainBytes = plaintext.getBytes("UTF-8");

                byte[] a = null;
                byte[] b = null;

                {
                    Cipher cipher = ThreadLocalSecurity.aes.get();
                    SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                    cipher.init(Cipher.ENCRYPT_MODE, key);
                    a = cipher.update(entropy);
                    byte[] encrypted = b = cipher.doFinal(plainBytes);
                    byte[] combined = new byte[a.length + b.length];
                    int x = 0;
                    for (; x < a.length; ++x) {
                        combined[x]=a[x];
                    }
                    for (; x - a.length < b.length; ++x) {
                        combined[x]=b[x - a.length];
                    }
                    byte[] encryptedValue = Base64.encodeBase64(combined);
                    sb.append(new String(encryptedValue, "UTF-8")).append('.');
                }
                {
                    HMac hmac = ThreadLocalSecurity.sha256hmac.get();
                    hmac.init(new KeyParameter(credentials.hash_key));
                    byte[] result = new byte[hmac.getMacSize()];
                    hmac.update(entropy, 0, entropy.length);
                    hmac.update(plainBytes, 0, plainBytes.length);
                    hmac.doFinal(result, 0);
                    sb.append(new String(Base64.encodeBase64(result), "UTF-8"));
                }
                return sb.toString();
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
                    case "2":
                        int j = ciphertext.indexOf('.', i+1);
                        if (j == -1) throw new RuntimeException();
                        byte plaintext[] = ciphertext.substring(i + 1, j).getBytes("UTF-8");
                        Cipher cipher = ThreadLocalSecurity.aes.get();
                        SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        byte[] decodedBytes = Base64.decodeBase64(plaintext);
                        byte[] original = cipher.doFinal(decodedBytes);
                        byte[] data = Arrays.copyOfRange(original, 64, original.length);
                        String cookieData = new String(data, "UTF-8");
                        byte[] hashCheck = Base64.decodeBase64(ciphertext.substring(j+1));
                        HMac hmac = ThreadLocalSecurity.sha256hmac.get();
                        hmac.init(new KeyParameter(credentials.hash_key));
                        byte[] result = new byte[hmac.getMacSize()];
                        hmac.update(original, 0, original.length);
                        hmac.doFinal(result, 0);
                        for(int k = 0; k < result.length; ++k) {
                            if (hashCheck.length < k) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                            if (hashCheck[k]!=result[k]) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                        }
                        return cookieData; // OK!!!
                    case "1":
                        // upgrade cookies
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

        req.response().headers().add("Set-Cookie", formatSetCookie(cookieName, cfCookie, req.isSSL()));
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
