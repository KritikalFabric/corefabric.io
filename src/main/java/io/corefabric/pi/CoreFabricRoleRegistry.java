package io.corefabric.pi;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.Role;
import org.kritikal.fabric.core.RoleRegistry;
import org.kritikal.fabric.dtn.jdtn.JsonConfigShim;

import java.util.LinkedList;
import java.util.List;

import static org.kritikal.fabric.CoreFabric.getVertx;
import static org.kritikal.fabric.CoreFabric.logger;

/**
 * Created by ben on 11/29/16.
 */
public class CoreFabricRoleRegistry {

    public static void addCoreFabricRoles(final Vertx vertx) {

        final String[] dtnRoles = new String[] {"dtn-router", "dtn-node", "dtn-mqtt-bridge"};

        if (RoleRegistry.hasRoleInSet(dtnRoles)) {
            org.kritikal.fabric.dtn.jdtn.JsonConfigShim.bootstrap();
        }

        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker"}, "app-config-db", (future, array) -> {

            JsonObject config = null!=array && array.size() > 0 ? array.getJsonObject(0) : new JsonObject();
            DeploymentOptions deploymentOptions = new DeploymentOptions();
            deploymentOptions.setWorker(true);
            JsonArray ary = new JsonArray();
            ary.add("corefabric.app-config-db.demo");
            config.put("addresses", ary);
            config.put("db_ref", "config_db");
            deploymentOptions.setConfig(config);
            vertx.deployVerticle("io.corefabric.pi.db.AppConfigDbVerticle", deploymentOptions, f1 -> {
                if (f1.failed()) future.fail("app-config-db");
                else future.complete();
            });

        }));

        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker", "app-config-db"}, "app-config-server", (future, array) -> {

            JsonObject config = null!=array && array.size() > 0 ? array.getJsonObject(0) : new JsonObject();
            DeploymentOptions deploymentOptions = new DeploymentOptions();
            deploymentOptions.setWorker(false);
            deploymentOptions.setConfig(config);
            vertx.deployVerticle("io.corefabric.pi.AppConfigServerVerticle", deploymentOptions, f1 -> {
                if (f1.failed()) future.fail("app-config-server");
                else future.complete();
            });

        }));

        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker"}, "app-web", (future, array) -> {

            JsonObject config = null!=array && array.size() > 0 ? array.getJsonObject(0) : new JsonObject();
            DeploymentOptions deploymentOptions = new DeploymentOptions();
            deploymentOptions.setWorker(true);
            deploymentOptions.setInstances(1);
            deploymentOptions.setConfig(config);
            Future future1 = Future.future();
            vertx.deployVerticle("io.corefabric.pi.AppWebServerVerticle", deploymentOptions, f1 -> {
                if (f1.failed()) future1.fail("app-web");
                else future1.complete();
            });

            Future future2 = Future.future();
            {
                JsonObject c = new JsonObject();
                JsonArray addresses = new JsonArray();
                addresses.add("ui.docapi");
                c.put("addresses", addresses);

                DeploymentOptions deploymentOptions1 = new DeploymentOptions();
                deploymentOptions1.setConfig(c);
                deploymentOptions1.setInstances(CoreFabric.ServerConfiguration.threads);
                deploymentOptions1.setWorker(true);

                vertx.deployVerticle("io.corefabric.pi.appweb.UIDocApiWorkerVerticle", deploymentOptions1, f2 -> {
                    if (f2.failed()) future2.fail("ui-docapi");
                    else future2.complete();
                });
            }

            CompositeFuture f = CompositeFuture.all(future1, future2);
            f.setHandler(ar -> {
                if (ar.failed()) future.fail("app-web");
                else future.complete();
            });

        }));
        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker"}, "dtn-router", (future, array)->{
            future.complete();
        }));
        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker"}, "dtn-node", (future, array) -> {
            future.complete();
        }));
        RoleRegistry.addRole(new Role(new String[] {}, "mqtt-broker", (future, array) -> {
            getVertx().executeBlocking((future1)->{
                try {
                    JsonConfigShim.bootstrap();
                }
                catch (Throwable t) {
                    logger.fatal("mqtt-broker", t);
                    future1.fail(t);
                }
            }, true, result->{
                if (result.succeeded()) future.complete();
                else future.fail(result.cause());
            });
        }));
        RoleRegistry.addRole(new Role(new String[] {"mqtt-broker"}, "dtn-mqtt-bridge", (future, array) -> {
            List<Future> futures = new LinkedList<Future>();
            for (int i = 0, l = null!=array?array.size():0; i < l; ++i) {
                final String label = "dtn-mqtt-bridge-" + i;
                JsonObject config = array.getJsonObject(i);
                DeploymentOptions deploymentOptions = new DeploymentOptions();
                deploymentOptions.setWorker(true);
                deploymentOptions.setConfig(config);
                Future future1 = Future.future();
                vertx.deployVerticle("org.kritikal.fabric.daemon.MqttBridgeVerticle", deploymentOptions, f1 -> {
                    if (f1.failed()) future1.fail(label);
                    else future1.complete();
                });
                futures.add(future1);
            }
            CompositeFuture cf = CompositeFuture.all(futures);
            cf.setHandler(ar -> {
                if (ar.failed()) future.fail("dtn-mqtt-bridge");
                else future.complete();
            });
        }));
    }


}
