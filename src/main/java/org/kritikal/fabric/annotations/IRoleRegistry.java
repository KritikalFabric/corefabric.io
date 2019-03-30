package org.kritikal.fabric.annotations;

import io.vertx.core.Vertx;

public interface IRoleRegistry {
    void addRoles(Vertx vertx);
}