package org.kritikal.fabric.net.http;

import com.datastax.driver.core.utils.UUIDs;

import java.util.UUID;

public class CFCookie {
    public CFCookie() {
        this.session_uuid = UUIDs.timeBased();
        this.originalCookieValue = this.session_uuid.toString();
        is_new = true;
    }
    public CFCookie(String originalCookieValue, UUID session_uuid) {
        this.session_uuid = session_uuid;
        this.originalCookieValue = originalCookieValue;
        is_new = false;
    }
    protected String originalCookieValue;
    public UUID session_uuid;
    public boolean is_new;
    public String cookieValue() {
        return originalCookieValue;
    }
    /*
    public void renew() {
        this.session_uuid = UUID.randomUUID();
        this.originalCookieValue = this.session_uuid.toString();
        this.is_new = true;
    }

     */
}
