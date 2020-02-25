package org.kritikal.fabric.net.http;

import com.datastax.driver.core.utils.UUIDs;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.CFCookieEncrypt;
import org.kritikal.fabric.net.ThreadLocalSecurity;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.kritikal.fabric.net.http.DefaultCFCookieCutter.formatSetCookie;

public class SecureCFCookieCutter implements CFCookieCutter {
    public final static boolean DEBUG = false;
    public static class Credentials {
        public byte[] code_key, hash_key;
        public final ThreadLocal<SecretKey> coding_key = new ThreadLocal<>() {
            @Override
            protected SecretKey initialValue() {
                return new SecretKeySpec(code_key, "Blowfish");
            }
        };
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
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid, UUID nonce_uuid, UUID credential_uuid) {
            super(originalCookieValue, session_uuid);
            this.nonce_uuid = nonce_uuid;
            this.credential_uuid = credential_uuid;
        }
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid, UUID nonce_uuid) {
            super(originalCookieValue, session_uuid);
        }
        protected SecureCFCookie(String originalCookieValue, UUID session_uuid) {
            super(originalCookieValue, session_uuid);
        }
        public SecureCFCookie() {
            super();
            changed = true;
        }

        private boolean changed = false;
        private UUID nonce_uuid = null;
        private UUID credential_uuid = null;

        public boolean hasCredentials() {
            return null != credential_uuid && null != nonce_uuid;
        }

        public boolean hasStorage() {
            return null != nonce_uuid;
        }

        public UUID getCredential() {
            return credential_uuid;
        }

        public UUID getNonce() {
            return nonce_uuid;
        }

        public void clear(boolean force) {
            if (null != this.nonce_uuid || null != this.credential_uuid || force) {
                this.nonce_uuid = null;
                this.credential_uuid = null;
                changed = true;
            }
        }

        public void reset() {
            this.originalCookieValue = null;
            this.session_uuid = UUIDs.timeBased();
            this.nonce_uuid = null;
            this.credential_uuid = null;
            is_new = true;
            changed = true;
        }

        public void associate(UUID nonce_uuid) {
            if (this.nonce_uuid == null || !this.nonce_uuid.equals(nonce_uuid)) {
                this.nonce_uuid = nonce_uuid;
                this.credential_uuid = null;
                changed = true;
            }
        }

        public void associate(UUID nonce_uuid, UUID credential_uuid) {
            if (this.nonce_uuid == null || !this.nonce_uuid.equals(nonce_uuid)) {
                this.nonce_uuid = nonce_uuid;
                changed = true;
            }
            if (null != this.credential_uuid && !this.credential_uuid.equals(credential_uuid)) {
                throw new RuntimeException();
            } else if (null == this.credential_uuid) {
                this.credential_uuid = credential_uuid;
                changed = true;
            }
        }

        /*
        @Override
        public void renew() {
            super.renew();
            changed = true;
        }

         */

        @Override
        public String cookieValue() {
            String originalValue = super.cookieValue();
            if (null==originalValue || changed) {
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
                StringBuilder sb = new StringBuilder();
                sb.append('4');
                if (null != cookie.nonce_uuid && null != cookie.credential_uuid) {
                    sb.append('$');
                } else if (null != cookie.nonce_uuid && null == cookie.credential_uuid) {
                    sb.append('#');
                } else {
                    sb.append('.');
                }
                byte[] entropy = ThreadLocalSecurity.secureRandom.get().getSeed(2048);
                byte[] session_uuid_data = UuidUtils.asBytes(cookie.session_uuid);
                byte[] nonce_uuid_data = null != cookie.nonce_uuid ? UuidUtils.asBytes(cookie.nonce_uuid) : new byte[0];
                byte[] credential_uuid_data = null != cookie.credential_uuid ? UuidUtils.asBytes(cookie.credential_uuid) : new byte[0];
                byte[] plainBytes = new byte[session_uuid_data.length + nonce_uuid_data.length + credential_uuid_data.length];
                int i = 0;
                for (int j = 0; j < session_uuid_data.length; ) {
                    plainBytes[i++] = session_uuid_data[j++];
                }
                for (int j = 0; j < nonce_uuid_data.length; ) {
                    plainBytes[i++] = nonce_uuid_data[j++];
                }
                for (int j = 0; j < credential_uuid_data.length; ) {
                    plainBytes[i++] = credential_uuid_data[j++];
                }

                byte[] a = null; // initialisation vector
                byte[] b = null; // cookie data encrypted

                {
                    Cipher cipher = ThreadLocalSecurity.blowfish.get();
                    cipher.init(Cipher.ENCRYPT_MODE, credentials.coding_key.get());
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

        public CFCookie decrypt(String ciphertext) {
            if (null == ciphertext || ciphertext.length() < 3) return new SecureCFCookie();

            try {
                switch (ciphertext.substring(0, 1)) {
                    case "4": {
                        boolean hasNonceUUID = false;
                        boolean hasCredentialUUID = false;
                        switch (ciphertext.substring(1, 2)) {
                            case ".":
                                break;
                            case "#":
                                hasNonceUUID = true;
                                break;
                            case "$":
                                hasNonceUUID = true;
                                hasCredentialUUID = true;
                                break;
                        }
                        int j = ciphertext.indexOf('.', 2);
                        if (j == -1) throw new RuntimeException();
                        byte plaintext[] = ciphertext.substring(2, j).getBytes("UTF-8");
                        Cipher cipher = ThreadLocalSecurity.blowfish.get();

                        cipher.init(Cipher.DECRYPT_MODE, credentials.coding_key.get());
                        byte[] decodedBytes = Base64.decodeBase64(plaintext);
                        byte[] original = cipher.doFinal(decodedBytes);
                        byte[] data = Arrays.copyOfRange(original, 2048, original.length);

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
                            if (hasCredentialUUID && hasNonceUUID) {
                                byte nonce_uuid_data[] = Arrays.copyOfRange(data, 16, 32);
                                UUID nonce_uuid = UuidUtils.asUuid(nonce_uuid_data);
                                byte credential_uuid_data[] = Arrays.copyOfRange(data, 32, 48);
                                UUID credential_uuid = UuidUtils.asUuid(credential_uuid_data);
                                return new SecureCFCookie(ciphertext, session_uuid, nonce_uuid, credential_uuid);
                            } else if (hasNonceUUID) {
                                byte nonce_uuid_data[] = Arrays.copyOfRange(data, 16, 32);
                                UUID nonce_uuid = UuidUtils.asUuid(nonce_uuid_data);
                                return new SecureCFCookie(ciphertext, session_uuid, nonce_uuid);
                            } else {
                                return new SecureCFCookie(ciphertext, session_uuid);
                            }
                        }
                    }
                    break;
                    default:
                        throw new RuntimeException();
                }
            }
            catch (Throwable t) {
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
    public static final String cookieName(String host) {
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

        if (cfCookie.is_new || cfCookie.changed) {
            final String s = formatSetCookie(cookieName, cfCookie, req.isSSL(), req.host());
            req.response().headers().add("Set-Cookie", s);
            if (SecureCFCookieCutter.DEBUG) CoreFabric.logger.warn(req.host() + req.path() + "\t" + cfCookie.session_uuid.toString() + "\tset (cut)\t" + s);
            cfCookie.is_new = false;
            cfCookie.changed = false;
        }
        if (req.isSSL()) {
            var cookie = req.getCookie("cf_http");
            if (null != cookie) {
                req.response().headers().add("Set-Cookie", "cf_http=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
            }
        }
        return cfCookie;
    }

    @Override
    public void apply(HttpServerRequest req, CFCookie cfCookie) {
        if (((SecureCFCookie)cfCookie).changed || cfCookie.is_new) {
            String cookieName = req.isSSL() ? cookieName(req.host()) : "cf_http";
            req.response().headers().remove("Set-Cookie");
            final String s = formatSetCookie(cookieName, cfCookie, req.isSSL(), req.host());
            req.response().headers().add("Set-Cookie", s);
            if (SecureCFCookieCutter.DEBUG) CoreFabric.logger.warn(req.host() + req.path() + "\t" + cfCookie.session_uuid.toString() + "\tset (apply)\t" + s);
        }
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
