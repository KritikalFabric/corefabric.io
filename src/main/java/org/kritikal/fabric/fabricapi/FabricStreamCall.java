package org.kritikal.fabric.fabricapi;

import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

public class FabricStreamCall {
    public FabricStreamCall(RoutingContext rc) {
        this.rc = rc;
        this.uuid = UUID.randomUUID();
    }
    public final RoutingContext rc;
    public final UUID uuid;
}
