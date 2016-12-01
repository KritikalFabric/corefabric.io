package io.corefabric.pi;

import io.vertx.core.Vertx;

import static org.kritikal.fabric.CoreFabric.globalConfig;

/**
 * Created by ben on 11/29/16.
 */
public class CoreFabricConfigShims {

    public static void apply(Vertx vertx) {
        org.kritikal.fabric.core.ConfigurationManager.Shim.apply(globalConfig);
        org.kritikal.fabric.dtn.jdtn.JsonConfigShim.apply(vertx, globalConfig);
    }

}
