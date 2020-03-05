package org.kritikal.fabric.net.mqtt;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface IAsyncMqttBroker {

    void apiPeek(String topic, Handler<JsonObject> handler);

    void apiPublish(String topic, byte[] body, int qos, boolean retained, Handler<Void> handler);

    void apiPublish(String topic, byte[] body, int qos, boolean retained, long ttl, Handler<Void> handler);

    void apiRemove(String topic, Handler<Void> handler);

}
