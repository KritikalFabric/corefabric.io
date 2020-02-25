package org.kritikal.fabric.fabricapi;

import com.datastax.driver.core.utils.UUIDs;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

public class FabricStreamCall {
    public FabricStreamCall(RoutingContext rc) {
        this.rc = rc;
        this.uuid = UUIDs.timeBased();
    }
    public final RoutingContext rc;
    public final UUID uuid;
}
