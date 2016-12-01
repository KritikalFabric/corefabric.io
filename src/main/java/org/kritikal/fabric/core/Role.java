package org.kritikal.fabric.core;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

import java.util.function.BiConsumer;

/**
 * Created by ben on 10/30/16.
 */
public final class Role {

    public Role(String[] depends, String roleName, BiConsumer<Future, JsonArray> startRoleVerticle) {
        this.depends = depends;
        this.roleName = roleName;
        this.startRoleVerticle = startRoleVerticle;
    }

    public String[] depends;
    public String roleName;
    public BiConsumer<Future, JsonArray> startRoleVerticle;
}
