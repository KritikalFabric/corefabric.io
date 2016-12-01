package org.kritikal.fabric.daemon;

import io.netty.buffer.ByteBuf;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.VERTXDEFINES;
import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.IMqttBroker;
import org.kritikal.fabric.net.mqtt.MqttBroker;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.kritikal.fabric.net.mqtt.codec.DecodePublish;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

import java.io.UnsupportedEncodingException;

/**
 * Created by ben on 04/02/15.
 */
public class MqttBrokerVerticle extends AbstractVerticle /*implements Handler<Message<Buffer>>*/ {

    final static Logger logger = LoggerFactory.getLogger(MqttBrokerVerticle.class);

    static MqttBroker mqttBroker = null;

    NetServer netServer = null;
    HttpServer httpServer = null;
    HttpServer httpServerForSockJS = null;

    //MessageConsumer<Buffer> mcMqttEnqueue = null;
    MessageConsumer<JsonObject> mcMqttPublish = null;
    MessageConsumer<JsonObject> mcMqttSubscribe = null;
    MessageConsumer<JsonObject> mcMqttUnsubscribe = null;
    MessageConsumer<JsonObject> mcMqttPeek = null;
    MessageConsumer<JsonObject> mcMqttBroadcast = null;

    /*@Override
    public void handle(Message<Buffer> event) {
        if (event.body() == null) return;
        try {
            BufferContainer bufferContainer = new BufferContainer();
            bufferContainer.append(event.body());
            PublishMessage publishMessage = DecodePublish.decode(bufferContainer, true);
            if (publishMessage.origin.equals(CoreFabric.ServerConfiguration.instance)) return;
            mqttBroker.enqueue(null, publishMessage);
        }
        catch (Throwable t) {
            logger.warn("", t);
        }
    }*/

    @Override
    public void start() throws Exception {
        super.start();

        final Logger logger = LoggerFactory.getLogger(getClass());

        if (mqttBroker == null) mqttBroker = new MqttBroker(getVertx());

        //mcMqttEnqueue = vertx.eventBus().consumer("mqtt.enqueue");
        //mcMqttEnqueue.handler(this);

        {
            NetServerOptions options = new NetServerOptions();
            options.setSoLinger(0);
            options.setTcpNoDelay(true);
            options.setTcpKeepAlive(true);
            options.setUsePooledBuffers(true);
            netServer = getVertx().createNetServer(options);
            netServer.connectHandler(new Handler<NetSocket>() {
                @Override
                public void handle(NetSocket netSocket) {
                    if (mqttBroker.DEBUG) {
                        logger.debug("Incoming connection MQTT");
                    }
                    final MqttBroker.MyMqttServerProtocol mqttServerProtocol = new MqttBroker.MyMqttServerProtocol(logger, getVertx(), mqttBroker, netSocket);
                    mqttBroker.waitingForConnect.add(mqttServerProtocol);
                }
            });
            netServer.listen(1883);
        }

        /*{
            HttpServerOptions options = new HttpServerOptions();
            options.setSoLinger(0);
            options.setTcpNoDelay(true);
            options.setTcpKeepAlive(true);
            options.setUsePooledBuffers(true);
            options.setHandle100ContinueAutomatically(true);
            httpServer = getVertx().createHttpServer();
            httpServer.websocketHandler(new Handler<ServerWebSocket>() {
                @Override
                public void handle(ServerWebSocket webSocket) {
                    if (mqttBroker.DEBUG) {
                        logger.debug("Incoming connection MQTT/WebSockets");
                    }
                    if (!"/mqtt".equals(webSocket.path())) webSocket.reject();
                    else {
                        final MqttBroker.MyMqttServerProtocol mqttServerProtocol = new MqttBroker.MyMqttServerProtocol(logger, getVertx(), mqttBroker, webSocket, corefabric);
                        mqttBroker.waitingForConnect.add(mqttServerProtocol);
                    }
                }
            });
            httpServer.listen(11884);
        }*/

        ///httpServerForSockJS = getVertx().createHttpServer();
        // vertx2 httpServerForSockJS.setTCPNoDelay(true);
        // vertx2 httpServerForSockJS.setUsePooledBuffers(true);
        // vertx2 sockJSServer = getVertx().createSockJSServer(httpServerForSockJS);
        // vertx2 JsonObject sockJSConfig = new JsonObject().put("prefix", "/mqtt");
        // vertx2 sockJSServer.installApp(sockJSConfig, new Handler<SockJSSocket>() {
        // vertx2     @Override
        // vertx2     public void handle(SockJSSocket sockJSSocket) {
        // vertx2         if (mqttBroker.DEBUG) {
        // vertx2             logger.debug("Incoming connection MQTT/SockJS");
        // vertx2         }
        // vertx2         final MqttBroker.MyMqttServerProtocol mqttServerProtocol = mqttBroker.new MyMqttServerProtocol(logger, getVertx(), mqttBroker, sockJSSocket);
        // vertx2         mqttBroker.waitingForConnect.add(mqttServerProtocol);
        // vertx2     }
        // vertx2 });
        // FIXME sockjs support
        //httpServerForSockJS.listen(11885);

        mcMqttPublish = vertx.eventBus().localConsumer("mqtt.publish", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                if (mqttApiCall.containsKey("stringBody")) {
                    try {
                        mqttBroker.apiPublish(
                                mqttApiCall.getString("topic"),
                                mqttApiCall.getString("stringBody").getBytes("UTF-8"),
                                mqttApiCall.getInteger("qos"),
                                mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                        );
                    } catch (UnsupportedEncodingException ex) {
                        logger.debug("Can't find UTF-8", ex);
                    }
                } else {
                    mqttBroker.apiPublish(
                            mqttApiCall.getString("topic"),
                            mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : null,
                            mqttApiCall.getInteger("qos"),
                            mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                    );
                }
            }
        });

        mcMqttSubscribe = vertx.eventBus().localConsumer("mqtt.subscribe", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                mqttBroker.apiSubscribe(
                        mqttApiCall.getString("topic"),
                        mqttApiCall.getString("bus-address")
                );
            }
        });

        mcMqttUnsubscribe = vertx.eventBus().localConsumer("mqtt.unsubscribe", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                mqttBroker.apiUnsubscribe(
                        mqttApiCall.getString("topic"),
                        mqttApiCall.getString("bus-address")
                );
            }
        });

        mcMqttPeek = vertx.eventBus().localConsumer("mqtt.peek", new Handler<Message<JsonObject>>() { // peek at the last retained messsage on that subscription
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                event.reply(mqttBroker.apiPeek(
                        mqttApiCall.getString("topic")
                ), VERTXDEFINES.DELIVERY_OPTIONS);
            }
        });

        mcMqttBroadcast = vertx.eventBus().consumer("mqtt.broadcast", new Handler<Message<JsonObject>>() { // like publish but with a subscription instead of a topic
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                if (mqttApiCall.containsKey("stringBody")) {
                    try {
                        mqttBroker.apiBroadcast(
                                mqttApiCall.getString("topic"),
                                mqttApiCall.getString("stringBody").getBytes("UTF-8"),
                                mqttApiCall.getInteger("qos"),
                                mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                        );
                    } catch (UnsupportedEncodingException ex) {
                        logger.fatal("Can't find UTF-8 encoding.", ex);
                    }
                } else {
                    mqttBroker.apiBroadcast(
                            mqttApiCall.getString("topic"),
                            mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : null,
                            mqttApiCall.getInteger("qos"),
                            mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                    );
                }
            }
        });
    }

    public static IMqttBroker mqttBroker() { return mqttBroker; }

    @Override
    public void stop() throws Exception {

        //if (mcMqttEnqueue != null) mcMqttEnqueue.unregister();
        if (mcMqttBroadcast != null) mcMqttBroadcast.unregister();
        if (mcMqttPeek != null) mcMqttPeek.unregister();
        if (mcMqttUnsubscribe != null) mcMqttUnsubscribe.unregister();
        if (mcMqttSubscribe != null) mcMqttSubscribe.unregister();
        if (mcMqttPublish != null) mcMqttPublish.unregister();

        netServer.close();
        netServer = null;

        httpServer.close();
        httpServer = null;

        //httpServerForSockJS.close();
        //httpServerForSockJS = null;

        super.stop();
    }

}
