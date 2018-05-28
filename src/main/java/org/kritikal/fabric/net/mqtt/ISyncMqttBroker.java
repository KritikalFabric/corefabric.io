package org.kritikal.fabric.net.mqtt;

import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;
import org.kritikal.fabric.CoreFabric;

import java.util.function.Consumer;

/**
 * Created by ben on 11/29/16.
 */
public interface ISyncMqttBroker {
    void syncApiPurge();

    void syncApiPublish(String topic, byte[] body, int qos, boolean retained);

    void syncApiPublish(String topic, byte[] body, int qos, boolean retained, long ttl);

    void syncApiSubscribe(String topic, String endPoint);

    void syncApiSubscribe(String topic, Consumer<SyncMqttBroker.MessageEncapsulation> onMessage);

    void syncApiUnsubscribe(String topic, String endPoint);

    void syncApiUnsubscribe(String topic, Consumer<SyncMqttBroker.MessageEncapsulation> onMessage);

    JsonObject syncApiPeek(String topic);

    void syncApiBroadcast(String topic, byte[] body, int qos, boolean retained);

    void syncMessageForBroadcast(PublishMessage publishMessage);

    boolean SLOWER = CoreFabric.ServerConfiguration.SLOWER;
    boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;
    boolean VERBOSE = false;
}
