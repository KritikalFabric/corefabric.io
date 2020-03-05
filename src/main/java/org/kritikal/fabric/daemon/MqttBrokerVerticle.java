package org.kritikal.fabric.daemon;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import org.kritikal.fabric.core.VERTXDEFINES;
import org.kritikal.fabric.net.mqtt.IAsyncMqttBroker;
import org.kritikal.fabric.net.mqtt.ISyncMqttBroker;
import org.kritikal.fabric.net.mqtt.SyncMqttBroker;
import io.vertx.core.eventbus.Message;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

import java.io.UnsupportedEncodingException;

/**
 * Created by ben on 04/02/15.
 */
public class MqttBrokerVerticle extends AbstractVerticle {

    final static Logger logger = LoggerFactory.getLogger(MqttBrokerVerticle.class);

    static SyncMqttBroker syncMqttBroker = null;
    static AsyncMqttBroker asyncMqttBroker = null;

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
            syncBroker.enqueue(null, publishMessage);
        }
        catch (Throwable t) {
            logger.warn("", t);
        }
    }*/

    @Override
    public void start() throws Exception {
        super.start();

        final Logger logger = LoggerFactory.getLogger(getClass());

        if (syncMqttBroker == null) syncMqttBroker = new SyncMqttBroker(getVertx());
        if (asyncMqttBroker == null) asyncMqttBroker = new AsyncMqttBroker();

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
                    if (syncMqttBroker.DEBUG) {
                        logger.debug("Incoming connection MQTT");
                    }
                    final SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = new SyncMqttBroker.MyMqttServerProtocol(logger, getVertx(), syncMqttBroker, netSocket);
                    syncMqttBroker.waitingForConnect.add(mqttServerProtocol);
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
                    if (syncBroker.DEBUG) {
                        logger.debug("Incoming connection MQTT/WebSockets");
                    }
                    if (!"/mqtt".equals(webSocket.path())) webSocket.reject();
                    else {
                        final SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = new SyncMqttBroker.MyMqttServerProtocol(logger, getVertx(), syncBroker, webSocket, corefabric);
                        syncBroker.waitingForConnect.add(mqttServerProtocol);
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
        // vertx2         if (syncBroker.DEBUG) {
        // vertx2             logger.debug("Incoming connection MQTT/SockJS");
        // vertx2         }
        // vertx2         final SyncMqttBroker.MyMqttServerProtocol mqttServerProtocol = syncBroker.new MyMqttServerProtocol(logger, getVertx(), syncBroker, sockJSSocket);
        // vertx2         syncBroker.waitingForConnect.add(mqttServerProtocol);
        // vertx2     }
        // vertx2 });
        // FIXME sockjs support
        //httpServerForSockJS.listen(11885);

        mcMqttPublish = vertx.eventBus().localConsumer("mqtt.publish", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                long ttl = mqttApiCall.containsKey("ttl") ? mqttApiCall.getLong("ttl") : -1;
                if (mqttApiCall.containsKey("stringBody")) {
                    try {
                        if (ttl < 0) {
                            syncMqttBroker.syncApiPublish(
                                    mqttApiCall.getString("topic"),
                                    mqttApiCall.getString("stringBody").getBytes("UTF-8"),
                                    mqttApiCall.getInteger("qos"),
                                    mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                            );
                        } else {
                            syncMqttBroker.syncApiPublish(
                                    mqttApiCall.getString("topic"),
                                    mqttApiCall.getString("stringBody").getBytes("UTF-8"),
                                    mqttApiCall.getInteger("qos"),
                                    mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain"),
                                    ttl
                            );
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.debug("Can't find UTF-8", ex);
                    }
                } else {
                    if (ttl < 0) {
                        syncMqttBroker.syncApiPublish(
                                mqttApiCall.getString("topic"),
                                mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : null,
                                mqttApiCall.getInteger("qos"),
                                mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                        );
                    } else {
                        syncMqttBroker.syncApiPublish(
                                mqttApiCall.getString("topic"),
                                mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : null,
                                mqttApiCall.getInteger("qos"),
                                mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain"),
                                ttl
                        );
                    }
                }
                event.reply(null);
            }
        });

        mcMqttSubscribe = vertx.eventBus().localConsumer("mqtt.subscribe", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                syncMqttBroker.syncApiSubscribe(
                        mqttApiCall.getString("topic"),
                        mqttApiCall.getString("bus-address")
                );
                event.reply(null);
            }
        });

        mcMqttUnsubscribe = vertx.eventBus().localConsumer("mqtt.unsubscribe", new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                syncMqttBroker.syncApiUnsubscribe(
                        mqttApiCall.getString("topic"),
                        mqttApiCall.getString("bus-address")
                );
                event.reply(null);
            }
        });

        mcMqttPeek = vertx.eventBus().localConsumer("mqtt.peek", new Handler<Message<JsonObject>>() { // peek at the last retained messsage on that subscription
            @Override
            public void handle(Message<JsonObject> event) {
                JsonObject mqttApiCall = event.body();
                event.reply(syncMqttBroker.syncApiPeek(
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
                        syncMqttBroker.syncApiBroadcast(
                                mqttApiCall.getString("topic"),
                                mqttApiCall.getString("stringBody").getBytes("UTF-8"),
                                mqttApiCall.getInteger("qos"),
                                mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                        );
                    } catch (UnsupportedEncodingException ex) {
                        logger.fatal("Can't find UTF-8 encoding.", ex);
                    }
                } else {
                    syncMqttBroker.syncApiBroadcast(
                            mqttApiCall.getString("topic"),
                            mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : null,
                            mqttApiCall.getInteger("qos"),
                            mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain")
                    );
                }
                event.reply(null);
            }
        });
    }

    public static ISyncMqttBroker syncBroker() { return syncMqttBroker; }

    public static IAsyncMqttBroker asyncBroker() { return asyncMqttBroker; }

    public class AsyncMqttBroker implements IAsyncMqttBroker {
        public void apiPeek(String topic, Handler<JsonObject> handler) {
            final JsonObject mqttApiCall = new JsonObject();
            mqttApiCall.put("topic", topic);
            if (handler == null) {
                throw new RuntimeException("AsyncMqttBroker.apiPeek() requires handler");
            } else {
                vertx.eventBus().send("mqtt.peek", mqttApiCall, new Handler<AsyncResult<Message<JsonObject>>>() {
                    @Override
                    public void handle(AsyncResult<Message<JsonObject>> event) {
                        if (event.failed()) {
                            handler.handle(null);
                        } else {
                            handler.handle(event.result().body());
                        }
                    }
                });
            }
        }

        public void apiPublish(String topic, byte[] body, int qos, boolean retained, Handler<Void> handler) {
            final JsonObject mqttApiCall = new JsonObject();
            mqttApiCall.put("topic", topic);
            mqttApiCall.put("qos", qos);
            mqttApiCall.put("retain", retained);
            mqttApiCall.put("body", body);
            if (handler == null) {
                vertx.eventBus().send("mqtt.publish", mqttApiCall);
            } else {
                vertx.eventBus().send("mqtt.publish", mqttApiCall, (vo1d) -> {
                    handler.handle(null);
                });
            }
        }

        public void apiPublish(String topic, byte[] body, int qos, boolean retained, long ttl, Handler<Void> handler) {
            final JsonObject mqttApiCall = new JsonObject();
            mqttApiCall.put("topic", topic);
            mqttApiCall.put("qos", qos);
            mqttApiCall.put("retain", retained);
            mqttApiCall.put("body", body);
            mqttApiCall.put("ttl", ttl);
            if (handler == null) {
                vertx.eventBus().send("mqtt.publish", mqttApiCall);
            } else {
                vertx.eventBus().send("mqtt.publish", mqttApiCall, (vo1d) -> {
                    handler.handle(null);
                });
            }
        }

        public void apiRemove(String topic, Handler<Void> handler) {
            final JsonObject mqttApiCall = new JsonObject();
            mqttApiCall.put("topic", topic);
            mqttApiCall.put("qos", 0);
            mqttApiCall.put("retain", true);
            mqttApiCall.put("body", new byte[0]);
            mqttApiCall.put("ttl", 0);
            if (handler == null) {
                vertx.eventBus().send("mqtt.publish", mqttApiCall);
            } else {
                vertx.eventBus().send("mqtt.publish", mqttApiCall, (vo1d) -> {
                    handler.handle(null);
                });
            }
        }
    }

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
