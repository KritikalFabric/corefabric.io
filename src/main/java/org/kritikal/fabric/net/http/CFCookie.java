package org.kritikal.fabric.net.http;

import java.util.UUID;

public class CFCookie {
    public CFCookie() {
        this.session_uuid = UUID.randomUUID();
    }
    public CFCookie(UUID session_uuid) {
        this.session_uuid = session_uuid;
    }
    public final UUID session_uuid;
}
