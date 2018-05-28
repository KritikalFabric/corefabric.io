package org.kritikal.fabric.net.mqtt;

import org.kritikal.fabric.net.mqtt.entities.*;
import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.ISocket;
import org.kritikal.fabric.net.KillConnectionError;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by ben on 8/25/14.
 */
public class MqttServerProtocol implements Handler<Buffer> {

    final boolean DEBUG  = SyncMqttBroker.DEBUG && SyncMqttBroker.VERBOSE;

    final Logger logger;
    final ISocket socket;
    final IMqttServerCallback callback;
    final Vertx vertx;
    int keepaliveSeconds = 0;
    long keepaliveMillis = 0;
    public boolean cleanDisconnect = false;
    long closeTimerID = 0;
    public boolean noEcho = false;

    public enum TopLevelState {
        EXPECT_CONNECT,
        EXPECT_MESSAGE,
    }

    public enum ProtocolVersion {
        UNKNOWN,
        MQTT_3_1,
        MQTT_3_1_1,
        MOSQUITTO_BRIDGE,
    }

    TopLevelState topLevelState = TopLevelState.EXPECT_CONNECT;
    ProtocolVersion protocolVersion = ProtocolVersion.UNKNOWN;

    ConcurrentLinkedQueue<PublishMessage> inflightInboundQoS2Messages = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<PublishMessage> inflightOutboundQoS1MessagesPuback = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<PublishMessage> inflightOutboundQoS2MessagesPubrec = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<PublishMessage> inflightOutboundQoS2MessagesPubcomp = new ConcurrentLinkedQueue<>();

    BufferContainer bufferContainer = new BufferContainer();
    public short outboundMID = 1;

    public MqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final ISocket socket) {
        this.logger = logger;
        this.vertx = vertx;
        this.callback = callback;
        this.socket = socket;
        this.cleanDisconnect = false;
        this.socket.dataHandler(this);
        this.socket.closeHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                MqttServerProtocol.this.callback.disconnected(MqttServerProtocol.this);
            }
        });
    }

    /**
     * data handler for the connected netsocket
     *
     * @param buffer
     */
    public void handle(Buffer buffer) {
        bufferContainer.append(buffer);
        int messageType = -1;
        try {
            bufferContainer.setRollbackMark();
            while (!bufferContainer.isEmpty()) {
                messageType = (bufferContainer.peekFirstByte() & 0b11110000) >> 4;
                if (messageType == AbstractMessage.DISCONNECT) {
                    if (DEBUG) {
                        logger.debug("Disconnecting on client request");
                    }
                    cleanDisconnect = true;
                    socket.close();
                    return;
                }

                AbstractMessage message = null;
                try {
                    message = MqttCodec.decode(bufferContainer, (byte) messageType);
                } catch (BufferContainer.NeedMoreDataException ex) {
                    return;
                }
                if (message == null) {
                    logger.debug("Invalid message data");
                    return;
                }
                bufferContainer.moveRollbackMark();

                try {
                    switch (messageType) {
                        case AbstractMessage.CONNECT:
                            handle((ConnectMessage) message);
                            break;

                        case AbstractMessage.PINGREQ:
                            handle((PingReqMessage) message);
                            break;

                        case AbstractMessage.SUBSCRIBE:
                            handle((SubscribeMessage) message);
                            break;

                        case AbstractMessage.UNSUBSCRIBE:
                            handle((UnsubscribeMessage) message);
                            break;

                        case AbstractMessage.PUBLISH:
                            handle((PublishMessage) message);
                            break;

                        case AbstractMessage.PUBACK:
                            handle((PubAckMessage) message);
                            break;

                        case AbstractMessage.PUBREC:
                            handle((PubRecMessage) message);
                            break;

                        case AbstractMessage.PUBREL:
                            handle((PubRelMessage) message);
                            break;

                        case AbstractMessage.PUBCOMP:
                            handle((PubCompMessage) message);
                            break;
                    }
                } catch (Exception ex) {
                    logger.debug("Ignoring...", ex);
                }

                if (closeTimerID != 0) { vertx.cancelTimer(closeTimerID); closeTimerID = 0; }
                if (!noEcho && keepaliveMillis > 0) {
                    closeTimerID = vertx.setTimer(keepaliveMillis, new Handler<Long>() {
                        @Override
                        public void handle(Long event) {
                            if (DEBUG) {
                                logger.debug("Disconnecting due to time out");
                            }
                            vertx.executeBlocking(f -> {
                                socket.close();
                                f.complete();
                            }, r -> {
                                // nothing
                            });
                        }
                    });
                }
            }
        } catch (KillConnectionError error) {
            logger.fatal("Killing connection", error);
            socket.close();
        } catch (Exception ex) {
            logger.fatal("Euw, error... message type " + messageType, ex);
            socket.close();
        } finally {
            bufferContainer.rollbackToMark();
        }
    }

    void handle(ConnectMessage connectMessage)
            throws Exception, KillConnectionError {

        if (DEBUG) { logger.debug("Connect"); }

        keepaliveSeconds = connectMessage.getKeepAlive();
        keepaliveMillis = keepaliveSeconds * 1500;

        if (topLevelState == TopLevelState.EXPECT_CONNECT) {
            boolean ok = false;

            protocolVersion = ProtocolVersion.UNKNOWN;
            if (connectMessage.getProtocolVersion() == 4) {
                if ("MQTT".equals(connectMessage.getProtocolName()))
                    protocolVersion = ProtocolVersion.MQTT_3_1_1;
            }
            else if (connectMessage.getProtocolVersion() == 3) {
                if ("MQIsdp".equals(connectMessage.getProtocolName()))
                    protocolVersion = ProtocolVersion.MQTT_3_1;
            }
            else if (connectMessage.getProtocolVersion() == -125) { // standard, non-standard for bridges
                noEcho = true;
                if ("MQTT".equals(connectMessage.getProtocolName()))
                    protocolVersion = ProtocolVersion.MQTT_3_1_1;
                if ("MQIsdp".equals(connectMessage.getProtocolName()))
                    protocolVersion = ProtocolVersion.MOSQUITTO_BRIDGE;
            }
            if (protocolVersion == ProtocolVersion.UNKNOWN)
                throw new KillConnectionError("Protocol name: " + connectMessage.getProtocolName() + ", version=" + connectMessage.getProtocolVersion());

            if ((protocolVersion == ProtocolVersion.MQTT_3_1 &&
                    (connectMessage.getClientID().length() == 0 ||
                     connectMessage.getClientID().length() > 23)) ||
                (protocolVersion == ProtocolVersion.MQTT_3_1_1 &&
                     connectMessage.getClientID().length() == 0)) {
                ConnAckMessage message = new ConnAckMessage();
                message.setReturnCode((byte) 2);
                write(MqttCodec.encode(message));
                if (DEBUG) {
                    logger.debug("Disconnecting due to invalid protocol / clientID");
                }
                socket.close();
                return;
            } else if (connectMessage.isUserFlag() && connectMessage.isPasswordFlag()) {
                ok = callback.authorize(connectMessage.getUsername(), connectMessage.getPassword());
            } else
                ok = true;

            if (ok) {
                topLevelState = TopLevelState.EXPECT_MESSAGE;
                callback.connected(this, connectMessage);
                ConnAckMessage message = new ConnAckMessage();
                message.setReturnCode((byte) 0); // all good, fire away
                write(MqttCodec.encode(message));
                if (DEBUG) {
                    logger.debug("Sent ConAck");
                }
                callback.postConnAck(this, connectMessage);
                return;
            } else {
                ConnAckMessage message = new ConnAckMessage();
                message.setReturnCode((byte) 4);
                write(MqttCodec.encode(message));
                if (DEBUG) {
                    logger.debug("Sent ConAck disconnect");
                }
                if (DEBUG) {
                    logger.debug("Disconnecting due to invalid credentials");
                }
                socket.close();
                return;
            }
        } else
            throw new Exception();
    }

    void handle(PingReqMessage pingReqMessage) {

        if (DEBUG) { logger.debug("PingReq"); }

        PingRespMessage message = new PingRespMessage();
        write(MqttCodec.encode(message));
    }

    void handle(SubscribeMessage subscribeMessage) {

        if (DEBUG) { logger.debug("Subscribe"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            ConcurrentLinkedQueue<MqttSubscription> newSubscriptions = new ConcurrentLinkedQueue<>();

            SubAckMessage subAckMessage = new SubAckMessage();
            subAckMessage.setMessageID(subscribeMessage.getMessageID());
            for (SubscribeMessage.Couple couple : subscribeMessage.subscriptions())
            {
                MqttSubscription subscription = new MqttSubscription(couple.getTopicFilter(), couple.getQos());
                newSubscriptions.add(subscription);
                byte subscribedAtQos = callback.subscribe(this, subscription);
                AbstractMessage.QOSType qosType = AbstractMessage.QOSType.MOST_ONE; // qos 0
                switch (subscribedAtQos)
                {
                    case 1:
                        qosType = AbstractMessage.QOSType.LEAST_ONE; // qos 1
                        break;
                    case 2:
                        qosType = AbstractMessage.QOSType.EXACTLY_ONCE; // qos 2
                        break;
                }
                subAckMessage.addType(qosType);
            }
            write(MqttCodec.encode(subAckMessage));

            callback.publishRetained(this, newSubscriptions);
        }
        // else ignore
    }

    void handle(UnsubscribeMessage unsubscribeMessage) {

        if (DEBUG) { logger.debug("Unsubscribe"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            UnsubAckMessage unsubAckMessage = new UnsubAckMessage();
            unsubAckMessage.setMessageID(unsubscribeMessage.getMessageID());
            for (String topic : unsubscribeMessage.topicFilters())
                callback.unsubscribe(this, topic);
            write(MqttCodec.encode(unsubAckMessage));
        }
        // else ignore
    }

    void handle(PublishMessage publishMessage) {

        if (DEBUG) { logger.debug("Publish"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            switch (publishMessage.getQos().getValue())
            {
                case 0:
                    callback.messageArrived(this, publishMessage);
                    break;

                case 1: {
                    final PubAckMessage pubAckMessage = new PubAckMessage();
                    pubAckMessage.setMessageID(publishMessage.getMessageID());
                    write(MqttCodec.encode(pubAckMessage));
                    callback.messageArrived(this, publishMessage);
                    break;
                }

                case 2: {
                    final PubRecMessage pubRecMessage = new PubRecMessage();
                    pubRecMessage.setMessageID(publishMessage.getMessageID());
                    write(MqttCodec.encode(pubRecMessage));
                    inflightInboundQoS2Messages.add(publishMessage);
                    break;
                }

                case 3:
                    return;
            }
        }
    }

    void handle(PubAckMessage pubAckMessage)
    {
        if (DEBUG) { logger.debug("PubAck"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            final int messageID = pubAckMessage.getMessageID();
            inflightOutboundQoS1MessagesPuback.forEach(message -> {
                if (messageID == message.getMessageID()) {
                    inflightOutboundQoS1MessagesPuback.remove(message);
                    // TODO: bacllback.messageDelivered(message)
                }
            });
        }
    }

    void handle(PubRecMessage pubRecMessage)
    {
        if (DEBUG) { logger.debug("PubReq"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            final int messageID = pubRecMessage.getMessageID();
            inflightOutboundQoS2MessagesPubrec.forEach(message -> {
                if (messageID == message.getMessageID()) {
                    inflightOutboundQoS2MessagesPubrec.remove(message);
                    final PubRelMessage pubRelMessage = new PubRelMessage();
                    pubRelMessage.setQos(AbstractMessage.QOSType.LEAST_ONE);
                    pubRelMessage.setMessageID(messageID);
                    write(MqttCodec.encode(pubRelMessage));
                    inflightOutboundQoS2MessagesPubcomp.add(message);
                }
            });
        }
    }

    void handle(PubRelMessage pubRelMessage)
    {
        if (DEBUG) { logger.debug("PubRel"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            final int messageID = pubRelMessage.getMessageID();
            inflightInboundQoS2Messages.forEach(message -> {
                if (message.getMessageID() == messageID)
                {
                    inflightInboundQoS2Messages.remove(message);
                    final PubCompMessage pubCompMessage = new PubCompMessage();
                    pubCompMessage.setMessageID(messageID);
                    write(MqttCodec.encode(pubCompMessage));
                    callback.messageArrived(this, message);
                }
            });
        }
    }

    void handle(PubCompMessage pubCompMessage)
    {
        if (DEBUG) { logger.debug("PubComp"); }

        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            final int messageID = pubCompMessage.getMessageID();
            inflightOutboundQoS2MessagesPubcomp.forEach(message -> {
                if (messageID == message.getMessageID()) {
                    inflightOutboundQoS2MessagesPubcomp.remove(message);
                }
            });
        }
    }

    public void publish(String topic, byte[] payload, int qos, boolean retain)
    {
        PublishMessage publishMessage = new PublishMessage();
        publishMessage.setTopicName(topic);
        if (payload != null)
            publishMessage.setPayload(ByteBuffer.wrap(payload));
        publishMessage.setRetainFlag(retain);
        if (qos == 1 || qos == 2)
            publishMessage.setMessageID(new Integer(outboundMID++));
        switch (qos)
        {
            case 0:
                publishMessage.setQos(AbstractMessage.QOSType.MOST_ONE);
                break;
            case 1:
                publishMessage.setQos(AbstractMessage.QOSType.LEAST_ONE);
                break;
            case 2:
                publishMessage.setQos(AbstractMessage.QOSType.EXACTLY_ONCE);
                break;
            default:
                // oops
        }
        write(MqttCodec.encode(publishMessage));
        switch (qos)
        {
            case 1:
                inflightOutboundQoS1MessagesPuback.add(publishMessage);
                break;
            case 2:
                inflightOutboundQoS2MessagesPubrec.add(publishMessage);
                break;
        }
    }

    void write(Buffer buffer)
    {
        socket.write(buffer);
        // TODO: track reads for ping disconnect
    }

    public void forceDisconnect()
    {
        if (DEBUG)
        {
            logger.debug("Force disconnect called.");
        }
        socket.close();
    }
}
