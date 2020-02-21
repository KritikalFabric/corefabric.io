package org.kritikal.fabric.net;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.security.SecureRandom;

public class ThreadLocalSecurity {
    public final static ThreadLocal<SecureRandom> secureRandom = new ThreadLocal<>() {
        @Override
        protected SecureRandom initialValue() {
            try {
                return SecureRandom.getInstanceStrong();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };
    public final static ThreadLocal<Cipher> blowfish = new ThreadLocal<>() {
        @Override
        protected Cipher initialValue() {
            try {
                return Cipher.getInstance("Blowfish", new BouncyCastleProvider());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };
    public final static ThreadLocal<HMac> sha256hmac = new ThreadLocal<>() {
        @Override
        protected HMac initialValue() {
            try {
                return new HMac(new SHA256Digest());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

}
