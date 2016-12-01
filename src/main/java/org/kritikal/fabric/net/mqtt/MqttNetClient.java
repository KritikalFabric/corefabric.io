package org.kritikal.fabric.net.mqtt;

import org.kritikal.fabric.net.mqtt.entities.PublishMessage;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.net.NetSocket;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.NetClient;

import java.util.List;

public class MqttNetClient implements Handler<AsyncResult<NetSocket>> {

    public MqttNetClient(final String clientId, final Logger logger, final Vertx vertx, final IMqttClientCallback callback, final MqttClientProtocol.ProtocolVersion protocolVersion)
    {
        this.clientId = clientId;
        this.logger = logger;
        this.vertx = vertx;
        this.callback = callback;
        this.protocolVersion = protocolVersion;
        this.netClient = vertx.createNetClient();
    }

    final String clientId;
    final NetClient netClient;
    final Logger logger;
    final Vertx vertx;
    final IMqttClientCallback callback;
    final MqttClientProtocol.ProtocolVersion protocolVersion;
    MqttClientProtocol protocol = null;

    public void connect(int port, String host)
    {
        // vertx2 netClient.setTCPNoDelay(true);
        // vertx2 netClient.setConnectTimeout(5 * 1000);
        // vertx2 netClient.setReconnectAttempts(10000);
        // vertx2 netClient.setReconnectInterval(5 * 1000);
        netClient.connect(port, host, this);
    }

    @Override
    public void handle(AsyncResult<NetSocket> event)
    {
        if (event.succeeded()) {
            logger.warn("Connected...");
            protocol = new MqttClientProtocol(clientId, logger, vertx, callback, event.result(), 60, protocolVersion);
        }
        else {
            logger.warn("Connection failed.");
        }
    }

    public void subscribe(List<MqttSubscription> subscriptions)
    {
        protocol.subscribe(subscriptions);
    }

    public void unsubscribe(List<String> topics)
    {
        protocol.unsubscribe(topics);
    }

    public void publish(String topic, byte[] payload, int qos, boolean retain)
    {
        protocol.publish(topic, payload, qos, retain);
    }

    public void disconnect()
    {
        protocol.disconnect();
        try
        {
            netClient.close();
        }
        catch (Exception ex)
        {
            // ignore
        }
        protocol = null;
    }
}
