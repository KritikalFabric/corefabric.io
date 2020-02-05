package org.kritikal.fabric.net.http;

import java.util.UUID;

public class CFCookie {
    public CFCookie() {
        this.session_uuid = UUID.randomUUID();
        this.originalCookieValue = this.session_uuid.toString();
        is_new = true;
    }
    public CFCookie(String originalCookieValue, UUID session_uuid) {
        this.session_uuid = session_uuid;
        this.originalCookieValue = originalCookieValue;
        is_new = false;
    }
    private final String originalCookieValue;
    public final UUID session_uuid;
    public final boolean is_new;
    public String cookieValue() {
        return originalCookieValue;
    }
}
