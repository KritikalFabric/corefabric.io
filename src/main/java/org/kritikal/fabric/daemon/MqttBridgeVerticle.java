package org.kritikal.fabric.daemon;

import com.cisco.qte.jdtn.apps.AbstractApp;
import com.cisco.qte.jdtn.bp.*;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.datastax.driver.core.utils.UUIDs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.MqttSubscription;
import org.kritikal.fabric.net.mqtt.MqttTopic;
import org.kritikal.fabric.net.mqtt.codec.DecodePublish;
import org.kritikal.fabric.net.mqtt.codec.EncodePublish;
import org.kritikal.fabric.net.mqtt.entities.AbstractMessage;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by ben on 30/10/2016.
 */
public class MqttBridgeVerticle extends AbstractVerticle {

    public static Logger logger = LoggerFactory.getLogger(MqttBridgeVerticle.class);

    static AppRegistration appRegistration = null;

    public static class MqttBridgeApp extends AbstractApp {

        public static final String APP_NAME = "MqttBridge";
        public static final String APP_PATTERN =
                "MqttBridge" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
        public static final int QUEUE_DEPTH = 10;

        public MqttBridgeApp(String[] args) throws BPException {
            super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
        }

        @Override
        public void startupImpl() {
            // nothing
            appRegistration = getAppRegistration();
        }

        @Override
        public void shutdownImpl() {
            // nothing
        }

        @Override
        public void threadImpl() throws Exception {

            Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
            EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
            EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
            Payload payload = bundle.getPayloadBundleBlock().getPayload();
            if (GeneralManagement.isDebugLogging()) {
                logger.debug("Received Bundle from " + source);
                logger.debug("Bundle Dest = " + dest);
                logger.debug("Dest service path = " + dest.getServicePath());
            }

            /*EndPointId eid = BPManagement.getInstance().getEndPointIdStem().append("/MqttBridge");
            if (dest.equals(eid))*/ {
                if (GeneralManagement.isDebugLogging()) {
                    logger.debug("Got matching packet...");
                }
                BufferContainer bufferContainer = new BufferContainer();
                byte[] publishMessage = payload.getBodyDataBuffer();
                bufferContainer.append(Buffer.buffer(publishMessage));
                try {
                    PublishMessage publishMessage1 = DecodePublish.decode(bufferContainer, true);
                    MqttBrokerVerticle.syncBroker().syncApiPublish(publishMessage1.getTopicName(), publishMessage1.getPayload().array(), (int)(publishMessage1.getQos().getValue()), publishMessage1.isRetainFlag());
                }
                catch (BufferContainer.NeedMoreDataException ignore1) { }
            }

        }
    }

    public final String uuid = UUIDs.timeBased().toString();
    public long sequence = 0L;
    public MessageConsumer<JsonObject> mc = null;

    @Override
    public void start() throws Exception {

        super.start();

        final String subscription = config().getString("subscribe", "#");
        final String notSubscription = config().containsKey("not") ? config().getString("not") : null;
        final MqttSubscription not = notSubscription == null ? null : new MqttSubscription(notSubscription,(byte)2);
        final String toEndPointId = config().getString("to");
        final EndPointId to = EndPointId.createEndPointId(toEndPointId);

        mc = vertx.eventBus().localConsumer("mqtt.bridge." + uuid, event -> {
            final JsonObject mqttApiCall = event.body();
            if (not != null) {
                MqttTopic topic = new MqttTopic(mqttApiCall.getString("topic"));
                if (not.matches(topic)) {
                    return;
                }
            }

            try {
                PublishMessage publishMessage = new PublishMessage();
                publishMessage.setTopicName(mqttApiCall.getString("topic"));
                if (mqttApiCall.containsKey("stringBody")) {
                    publishMessage.setPayload(ByteBuffer.wrap(mqttApiCall.getString("stringBody").getBytes("UTF-8")));
                } else {
                    publishMessage.setPayload(ByteBuffer.wrap(mqttApiCall.containsKey("body") ? mqttApiCall.getBinary("body") : new byte[0]));
                }
                int qos = mqttApiCall.getInteger("qos");
                publishMessage.setQos(qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : (qos == 1 ? AbstractMessage.QOSType.MOST_ONE : AbstractMessage.QOSType.LEAST_ONE));
                publishMessage.setRetainFlag(mqttApiCall.containsKey("retain") && mqttApiCall.getBoolean("retain"));
                publishMessage.expires = mqttApiCall.containsKey("ttl") ? new java.util.Date().getTime() + mqttApiCall.getLong("ttl") : 0;
                publishMessage.origin = CoreFabric.ServerConfiguration.instance;

                vertx.executeBlocking(f -> {
                    ByteBuf bb = Unpooled.buffer();
                    try {
                        EndPointId source = BPManagement.getInstance().getEndPointIdStem().append("/MqttBridge/").append(uuid).append("/").append("" + sequence++);
                        EncodePublish.encode(publishMessage, bb, true);
                        Payload payload = new Payload(bb.array(), 0, bb.array().length);
                        BpApi.getInstance().sendBundle(
                                appRegistration,
                                source,
                                to,
                                payload,
                                null);
                        f.complete();
                    } catch (Throwable t) {
                        f.fail(t);
                    } finally {
                        bb.release();
                    }
                }, false, r -> {
                    if (r.failed()) logger.fatal("bridging mqtt", r.cause());
                });
            }
            catch (Throwable t) {
                logger.warn("", t);
            }
        });

        if (notSubscription == null)
            logger.info("Routing MQTT from [" + subscription + "] to " + toEndPointId);
        else
            logger.info("Routing MQTT from [" + subscription + "] to " + toEndPointId + " (ignoring " + notSubscription + ")");

        MqttBrokerVerticle.syncBroker().syncApiSubscribe(subscription, "mqtt.bridge." + uuid);
    }

    @Override
    public void stop() throws Exception {
        if (mc != null) {
            mc.unregister();
            mc = null;
        }

        super.stop();
    }

}
