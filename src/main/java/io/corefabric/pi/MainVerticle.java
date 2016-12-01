package io.corefabric.pi;

import io.vertx.core.*;
import org.kritikal.fabric.core.RoleRegistry;

/**
 * Created by ben on 07/03/2016.
 */
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception
    {
        super.start();

        vertx.deployVerticle("org.kritikal.fabric.daemon.MqttBrokerVerticle", new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> event) {
                if (event.failed()) { startFuture.fail("mqtt-broker"); return; }
                RoleRegistry.startAll(getVertx(), startFuture);
            }
        });
    }
}
