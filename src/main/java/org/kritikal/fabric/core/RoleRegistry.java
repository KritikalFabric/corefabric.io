package org.kritikal.fabric.core;

import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.CoreFabric;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by ben on 10/30/16.
 */
public class RoleRegistry {

    private final static Logger logger = LoggerFactory.getLogger(RoleRegistry.class);

    public enum State {
        COLD,
        OFF,
        ON,
        FAILED,
    }

    private final static ConcurrentHashMap<String, AbstractMap.SimpleEntry<Role, RoleRegistry.State>> roles = new ConcurrentHashMap<>();

    public static void addRole(Role role) {
        roles.put(role.roleName, new AbstractMap.SimpleEntry<>(role, "mqtt-broker".equals(role.roleName) ? State.ON : State.COLD));
    }

    public static void startAll(Vertx vertx, Future<Void> startFuture) {
        final JsonObject rolesJson = CoreFabric.globalConfig.getJsonObject("roles");
        List<Future> list = new ArrayList<>();
        roles.entrySet().forEach(entry -> {
            if (rolesJson.containsKey(entry.getKey())) {
                AbstractMap.SimpleEntry<Role, State> x = entry.getValue();
                if (x.getValue() == State.ON) return;
                list.add(startOne(x.getKey()));
            }
        });
        CompositeFuture f = CompositeFuture.all(list);
        f.setHandler(ar -> {
            if (ar.failed()) startFuture.fail(ar.cause());
            else startFuture.complete();
        });
    }

    static class FuturePerfect {
        FuturePerfect(Future<Void> future) {this.future = future;}
        final Future<Void> future;
        final ArrayList<Future> toNotify = new ArrayList<>();
    }
    private static ConcurrentHashMap<String, FuturePerfect> futures = new ConcurrentHashMap<>();
    private static Future startOne(Role role) {
        AbstractMap.SimpleEntry<Role, State> x = roles.get(role.roleName);
        FuturePerfect futurePerfect = futures.compute(x.getKey().roleName, (k, v) -> {
            if (v == null) {
                if (x.getValue() != State.COLD) throw new FabricError("Oops");
                x.setValue(State.OFF);

                v = new FuturePerfect(Future.future());
                final FuturePerfect fp = v;
                v.future.setHandler(ar -> {
                    if (ar.succeeded()) {
                        logger.info(x.getKey().roleName + " ... started");
                        x.setValue(State.ON);
                        fp.toNotify.forEach(Future::complete);
                    } else {
                        logger.fatal(x.getKey().roleName + " FAILED");
                        logger.warn(ar.cause());
                        x.setValue(State.FAILED);
                        fp.toNotify.forEach(future -> future.fail(x.getKey().roleName));
                    }
                });

                ArrayList<Future> list = new ArrayList<>();
                if (x.getKey().depends != null && x.getKey().depends.length > 0) {
                    for (String dependency : x.getKey().depends) {
                        AbstractMap.SimpleEntry<Role, State> d = roles.get(dependency);
                        if (d == null) {
                            list.add(Future.failedFuture(dependency));
                        } else {
                            if (d.getValue() == State.ON) continue;
                            list.add(startOne(d.getKey()));
                        }
                    }
                }

                final JsonObject rolesJson = CoreFabric.globalConfig.getJsonObject("roles");
                final Future f = v.future;
                CompositeFuture cf = CompositeFuture.all(list);
                cf.setHandler(ar -> {
                    if (ar.failed()) {
                        logger.fatal(x.getKey().roleName + " FAILED (dependencies)");
                        logger.warn(ar.cause());
                        x.setValue(State.FAILED);
                        f.fail(x.getKey().roleName);
                    } else {
                        x.getKey().startRoleVerticle.accept(f, rolesJson.getJsonArray(x.getKey().roleName));
                    }
                });
            }
            return v;
        });
        Future future = Future.future();
        futurePerfect.toNotify.add(future);
        return future;
    }

    public static boolean hasRoleInSet(String[] set) {
        for (String role : set) {
            if (roles.get(role) != null) return true;
        }
        return false;
    }

}
