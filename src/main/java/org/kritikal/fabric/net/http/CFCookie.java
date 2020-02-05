package org.kritikal.fabric.net.http;

import java.util.UUID;

public class CFCookie {
    public CFCookie() {
        this.session_uuid = UUID.randomUUID();
        is_new = true;
    }
    public CFCookie(UUID session_uuid) {
        this.session_uuid = session_uuid;
        is_new = false;
    }
    public final UUID session_uuid;
    public final boolean is_new;
}
