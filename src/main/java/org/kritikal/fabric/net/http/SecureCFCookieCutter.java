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
import org.kritikal.fabric.core.CFCookieEncrypt;
import org.kritikal.fabric.core.CFLogEncrypt;
import org.kritikal.fabric.net.ThreadLocalSecurity;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.kritikal.fabric.net.http.DefaultCFCookieCutter.formatSetCookie;

public class SecureCFCookieCutter implements CFCookieCutter {
    public static class Credentials {
        public byte[] code_key, hash_key;
    }
    public static class UuidUtils {
        public static UUID asUuid(byte[] bytes) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            bb.order(ByteOrder.BIG_ENDIAN);
            long firstLong = bb.getLong();
            long secondLong = bb.getLong();
            return new UUID(firstLong, secondLong);
        }

        public static byte[] asBytes(UUID uuid) {
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return bb.array();
        }
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
                return provider.encrypt(this);
            }
            return originalValue;
        }
    }
    public class SecureCookieEncrypt implements CFCookieEncrypt {
        @Override
        public String encrypt(CFCookie cfCookie) {
            try {
                SecureCFCookie cookie = (SecureCFCookie) cfCookie;
                String plaintext = cfCookie.session_uuid.toString();
                StringBuilder sb = new StringBuilder();
                sb.append("3.");
                byte[] entropy = ThreadLocalSecurity.secureRandom.get().getSeed(256);
                byte[] plainBytes = UuidUtils.asBytes(cookie.session_uuid);

                byte[] a = null; // initialisation vector
                byte[] b = null; // session_uuid
                byte[] c = null; // nonce_uuid
                byte[] d = null; // credential uuid

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

        private String legacy_decrypt(String ciphertext) {
            if (ciphertext == null) throw new RuntimeException();
            if (ciphertext.length() < 3) throw new RuntimeException();
            try {
                switch (ciphertext.substring(0, 1)) {
                    case "3": {
                        boolean hasNonceUUID = false;
                        boolean hasCredentialUUID = false;
                        switch (ciphertext.substring(1, 2)) {
                            case ".":
                                break;
                            case ",":
                                hasNonceUUID = true;
                                break;
                            case ";":
                                hasNonceUUID = true;
                                hasCredentialUUID = true;
                                break;
                        }
                        int j = ciphertext.indexOf('.', 2);
                        if (j == -1) throw new RuntimeException();
                        byte plaintext[] = ciphertext.substring(2, j).getBytes("UTF-8");
                        Cipher cipher = ThreadLocalSecurity.aes.get();
                        SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        byte[] decodedBytes = Base64.decodeBase64(plaintext);
                        byte[] original = cipher.doFinal(decodedBytes);
                        byte[] data = Arrays.copyOfRange(original, 256, original.length);
                        String cookieData = new String(data, "UTF-8");
                        byte[] hashCheck = Base64.decodeBase64(ciphertext.substring(j + 1));
                        HMac hmac = ThreadLocalSecurity.sha256hmac.get();
                        hmac.init(new KeyParameter(credentials.hash_key));
                        byte[] result = new byte[hmac.getMacSize()];
                        hmac.update(original, 0, original.length);
                        hmac.doFinal(result, 0);
                        for (int k = 0; k < result.length; ++k) {
                            if (hashCheck.length < k) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                            if (hashCheck[k] != result[k]) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                        }
                        return cookieData; // OK!!!
                    }
                    case "2": {
                        int j = ciphertext.indexOf('.', 2);
                        if (j == -1) throw new RuntimeException();
                        byte plaintext[] = ciphertext.substring(2, j).getBytes("UTF-8");
                        Cipher cipher = ThreadLocalSecurity.aes.get();
                        SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        byte[] decodedBytes = Base64.decodeBase64(plaintext);
                        byte[] original = cipher.doFinal(decodedBytes);
                        byte[] data = Arrays.copyOfRange(original, 64, original.length);
                        String cookieData = new String(data, "UTF-8");
                        byte[] hashCheck = Base64.decodeBase64(ciphertext.substring(j + 1));
                        HMac hmac = ThreadLocalSecurity.sha256hmac.get();
                        hmac.init(new KeyParameter(credentials.hash_key));
                        byte[] result = new byte[hmac.getMacSize()];
                        hmac.update(original, 0, original.length);
                        hmac.doFinal(result, 0);
                        for (int k = 0; k < result.length; ++k) {
                            if (hashCheck.length < k) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                            if (hashCheck[k] != result[k]) {
                                throw new RuntimeException("Invalid HMAC");
                            }
                        }
                        return cookieData; // OK!!!
                    }
                    case "1":
                        // upgrade cookies
                    default:
                        throw new RuntimeException();
                }
            }
            catch (Throwable t) {
                //CoreFabric.logger.fatal("log-encrypt", t);
                throw new RuntimeException(t);
            }
        }

        public CFCookie decrypt(String ciphertext) {
            if (null == ciphertext || ciphertext.length() < 3) return new SecureCFCookie();
            char v = ciphertext.charAt(0);
            if (v == '1' || v == '2') {
                try {
                    String plaintext = legacy_decrypt(ciphertext);
                    UUID try_parse = UUID.fromString(plaintext);
                    if (null != try_parse) {
                        return new SecureCFCookie(ciphertext, try_parse);
                    }
                } catch (Throwable t) {
                }
            } else {
                try {
                    switch (ciphertext.substring(0, 1)) {
                        case "3": {
                            boolean hasNonceUUID = false;
                            boolean hasCredentialUUID = false;
                            switch (ciphertext.substring(1, 2)) {
                                case ".":
                                    break;
                                case ",":
                                    hasNonceUUID = true;
                                    break;
                                case ";":
                                    hasNonceUUID = true;
                                    hasCredentialUUID = true;
                                    break;
                            }
                            int j = ciphertext.indexOf('.', 2);
                            if (j == -1) throw new RuntimeException();
                            byte plaintext[] = ciphertext.substring(2, j).getBytes("UTF-8");
                            Cipher cipher = ThreadLocalSecurity.aes.get();
                            SecretKey key = new SecretKeySpec(credentials.code_key, "AES");
                            cipher.init(Cipher.DECRYPT_MODE, key);
                            byte[] decodedBytes = Base64.decodeBase64(plaintext);
                            byte[] original = cipher.doFinal(decodedBytes);
                            byte[] data = Arrays.copyOfRange(original, 256, original.length);

                            byte[] hashCheck = Base64.decodeBase64(ciphertext.substring(j + 1));
                            HMac hmac = ThreadLocalSecurity.sha256hmac.get();
                            hmac.init(new KeyParameter(credentials.hash_key));
                            byte[] result = new byte[hmac.getMacSize()];
                            hmac.update(original, 0, original.length);
                            hmac.doFinal(result, 0);
                            for (int k = 0; k < result.length; ++k) {
                                if (hashCheck.length < k) {
                                    throw new RuntimeException("Invalid HMAC");
                                }
                                if (hashCheck[k] != result[k]) {
                                    throw new RuntimeException("Invalid HMAC");
                                }
                            }

                            if (data.length >= 16) {
                                byte session_uuid_data[] = Arrays.copyOfRange(data, 0, 16);
                                UUID session_uuid = UuidUtils.asUuid(session_uuid_data);
                                return new SecureCFCookie(ciphertext, session_uuid);
                            }
                        }
                        default:
                            throw new RuntimeException();
                    }
                }
                catch (Throwable t) {
                }
            }
            return new SecureCFCookie();
        }
    }
    public SecureCFCookie parse(String originalCookieValue) {
        return (SecureCFCookie) provider.decrypt(originalCookieValue);
    }
    public SecureCFCookieCutter(Credentials credentials) {
        this.credentials = credentials;
        this.provider = new SecureCookieEncrypt();
    }
    private final Credentials credentials;
    private final CFCookieEncrypt provider;
    private final String cookieName(String host) {
        int i = host.indexOf(':');
        if (i > -1) {
            return "cf__" + host.substring(0, i).replace('.', '_').replace('-', '_');
        }
        return "cf__" + host.replace('.', '_').replace('-', '_');
    }
    @Override
    public CFCookie cut(HttpServerRequest req) {
        String cookieName = req.isSSL() ? cookieName(req.host()) : "cf_http";
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

        req.response().headers().add("Set-Cookie", formatSetCookie(cookieName, cfCookie, req.isSSL(), req.host()));
        return cfCookie;
    }

    @Override
    public CFCookie cut(ServerWebSocket webSocket) {
        //String cookieName = cookieName(URI.create(webSocket.uri()).getHost());
        String cookieValue = null;
        try {
            String cookies = webSocket.headers().get("Cookie");
            Set<Cookie> cookieSet = CookieDecoder.decode(cookies);
            for (Cookie cookie : cookieSet) {
                if (cookie.getName().startsWith("cf__")) {
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
