package org.kritikal.fabric.core;

import org.kritikal.fabric.net.http.CFCookie;

public interface CFCookieEncrypt {
    String encrypt(CFCookie cfCookie);
    CFCookie decrypt(String ciphertext);
}
