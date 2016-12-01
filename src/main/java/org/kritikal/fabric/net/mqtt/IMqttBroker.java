package org.kritikal.fabric.net.mqtt;

import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;
import org.kritikal.fabric.CoreFabric;

import java.util.function.Consumer;

/**
 * Created by ben on 11/29/16.
 */
public interface IMqttBroker {
    void apiPurge();

    void apiPublish(String topic, byte[] body, int qos, boolean retained);

    void apiPublish(String topic, byte[] body, int qos, boolean retained, long ttl);

    void apiSubscribe(String topic, String endPoint);

    void apiSubscribe(String topic, Consumer<MqttBroker.MessageEncapsulation> onMessage);

    void apiUnsubscribe(String topic, String endPoint);

    void apiUnsubscribe(String topic, Consumer<MqttBroker.MessageEncapsulation> onMessage);

    JsonObject apiPeek(String topic);

    void apiBroadcast(String topic, byte[] body, int qos, boolean retained);

    void messageForBroadcast(PublishMessage publishMessage);

    boolean SLOWER = CoreFabric.ServerConfiguration.SLOWER;
    boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;
    boolean VERBOSE = false;
}
