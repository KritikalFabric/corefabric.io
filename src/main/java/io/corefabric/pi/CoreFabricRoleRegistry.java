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

import java.util.LinkedList;
import java.util.List;

/**
 * Created by ben on 11/29/16.
 */
public class CoreFabricRoleRegistry {

    public static void addCoreFabricRoles(final Vertx vertx) {

        final String[] dtnRoles = new String[] {"dtn-router", "dtn-node", "dtn-mqtt-bridge", "dtn-shell"};

        if (RoleRegistry.hasRoleInSet(dtnRoles)) {
            org.kritikal.fabric.dtn.jdtn.JsonConfigShim.bootstrap();
        }

        RoleRegistry.addRole(new Role(new String[] {"dtn-shell", "mqtt-broker"}, "app-web", (future, array) -> {

            JsonObject config = array.size() > 0 ? array.getJsonObject(0) : new JsonObject();
            DeploymentOptions deploymentOptions = new DeploymentOptions();
            deploymentOptions.setWorker(false);
            deploymentOptions.setConfig(config);
            Future future1 = Future.future();
            vertx.deployVerticle("io.corefabric.pi.AppWebServerVerticle", f1 -> {
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
        RoleRegistry.addRole(new Role(new String[] {}, "dtn-shell", (future, array) -> {
            vertx.deployVerticle("org.kritikal.fabric.dtn.jdtn.JDTNHTTPShell", f1 -> {
                if (f1.failed()) future.fail("dtn-shell");
                else future.complete();
            });
        }));
        RoleRegistry.addRole(new Role(new String[] {"dtn-shell", "mqtt-broker"}, "dtn-router", (future, array)->{
            future.complete();
        }));
        RoleRegistry.addRole(new Role(new String[] {"dtn-shell", "mqtt-broker"}, "dtn-node", (future, array) -> {
            future.complete();
        }));
        RoleRegistry.addRole(new Role(new String[] {}, "mqtt-broker", (future, array) -> {
            // do nothing this is dummy
            // TODO: switch on/off mqtt-listen on external ports according to this specific config path
            future.complete();
        }));
        RoleRegistry.addRole(new Role(new String[] {"dtn-shell", "mqtt-broker"}, "dtn-mqtt-bridge", (future, array) -> {
            List<Future> futures = new LinkedList<Future>();
            for (int i = 0, l = array.size(); i < l; ++i) {
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
