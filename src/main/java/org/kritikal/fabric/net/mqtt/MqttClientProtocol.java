package org.kritikal.fabric.net.mqtt;

import org.kritikal.fabric.net.mqtt.entities.*;
import org.kritikal.fabric.net.BufferContainer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.net.NetSocket;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by ben on 8/24/14.
 */
public class MqttClientProtocol implements Handler<Buffer> {

    final Logger logger;
    final NetSocket netSocket;
    final IMqttClientCallback callback;
    final Vertx vertx;
    final int keepaliveSeconds;
    final long keepaliveMillis;

    public enum TopLevelState {
        EXPECT_CONNACK,
        EXPECT_MESSAGE,
    }

    public enum ProtocolVersion {
        MQTT_3_1,
        MQTT_3_1_1,
    }

    TopLevelState topLevelState = TopLevelState.EXPECT_CONNACK;
    final ProtocolVersion protocolVersion;
    LinkedList<PublishMessage> inflightInboundQoS2Messages = new LinkedList<>();
    LinkedList<PublishMessage> inflightOutboundQoS1MessagesPuback = new LinkedList<>();
    LinkedList<PublishMessage> inflightOutboundQoS2MessagesPubrec = new LinkedList<>();
    LinkedList<PublishMessage> inflightOutboundQoS2MessagesPubcomp = new LinkedList<>();
    long lastCommunicationMs = 0;
    long pingTimerID = 0;
    BufferContainer bufferContainer = new BufferContainer();
    short outboundMID = 1;
    boolean cleanDisconnect = false;

    public MqttClientProtocol(final String clientId, final Logger logger, final Vertx vertx, final IMqttClientCallback callback, final NetSocket netSocket, final int keepaliveSeconds, final ProtocolVersion protocolVersion)
    {
        this.logger = logger;
        this.vertx = vertx;
        this.callback = callback;
        this.netSocket = netSocket;
        this.keepaliveSeconds = keepaliveSeconds;
        this.keepaliveMillis = 1000 * keepaliveSeconds;
        this.netSocket.handler(this);
        this.netSocket.closeHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                MqttClientProtocol.this.callback.disconnected(cleanDisconnect);
            }
        });
        this.protocolVersion = protocolVersion;

        final ConnectMessage message = new ConnectMessage();
        message.setCleanSession(true);
        message.setClientID(clientId);
        message.setKeepAlive(keepaliveSeconds);
        switch (this.protocolVersion)
        {
            case MQTT_3_1:
                message.setProtocolName("MQIsdp");
                message.setProtocolVersion((byte)3);
                break;

            case MQTT_3_1_1:
                message.setProtocolName("MQTT");
                message.setProtocolVersion((byte)4);
                break;
        }
        message.setWillFlag(false);
        message.setUserFlag(false);
        message.setPasswordFlag(false);
        write(MqttCodec.encode(message));
    }

    /**
     * data handler for the connected netsocket
     * @param buffer
     */
    public void handle(Buffer buffer)
    {
        bufferContainer.append(buffer);
        int messageType = -1;
        try {
            bufferContainer.setRollbackMark();
            while (!bufferContainer.isEmpty()) {
                messageType = (bufferContainer.peekFirstByte() & 0b11110000) >> 4;
                AbstractMessage message = null;
                try {
                    message = MqttCodec.decode(bufferContainer, (byte)messageType);
                }
                catch (BufferContainer.NeedMoreDataException ex) {
                    message = null;
                }
                if (message == null)
                    return;
                bufferContainer.moveRollbackMark();

                try {
                    switch (messageType) {
                        case AbstractMessage.CONNACK:
                            handle((ConnAckMessage) message);
                            break;

                        case AbstractMessage.PINGRESP:
                            // do nothing here ... handle((PingRespMessage)message);
                            break;

                        case AbstractMessage.SUBACK:
                            // do nothing here ... handle((SubAckMessage)message);
                            break;

                        case AbstractMessage.UNSUBACK:
                            // do nothing here ... handle((UnSubAckMessage)message);
                            break;

                        case AbstractMessage.PUBLISH:
                            handle((PublishMessage)message);
                            break;

                        case AbstractMessage.PUBREL:
                            handle((PubRelMessage)message);
                            break;

                        case AbstractMessage.PUBACK:
                            handle((PubAckMessage)message);
                            break;

                        case AbstractMessage.PUBREC:
                            handle((PubRecMessage)message);
                            break;

                        case AbstractMessage.PUBCOMP:
                            handle((PubCompMessage)message);
                            break;
                    }
                }
                catch (Exception ex) {
                    logger.warn("Ignoring...", ex);
                }
            }
        }
        catch (Exception ex) {
            logger.fatal("Euw, error... message type " + messageType, ex);
            netSocket.close();
        }
        finally {
            bufferContainer.rollbackToMark();
        }
    }

    void handle(ConnAckMessage connAckMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_CONNACK)
        {
            if (connAckMessage.getReturnCode() == 0) {
                // great, let's subscribe to everything
                topLevelState = TopLevelState.EXPECT_MESSAGE;
                callback.connectSuccessful();
            }
            else {
                callback.connectError(connAckMessage);
                netSocket.close();
            }
        }
        else
        {
            // ignore message
        }
    }

    void handle(PublishMessage publishMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            switch (publishMessage.getQos().getValue())
            {
                case 0:
                    callback.messageArrived(publishMessage);
                    break;

                case 1: {
                    final PubAckMessage pubAckMessage = new PubAckMessage();
                    pubAckMessage.setMessageID(publishMessage.getMessageID());
                    write(MqttCodec.encode(pubAckMessage));
                    callback.messageArrived(publishMessage);
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
        else
        {
            // ignore message
        }
    }

    void handle(PubRelMessage pubRelMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            int messageID = pubRelMessage.getMessageID();
            ListIterator<PublishMessage> iter = inflightInboundQoS2Messages.listIterator();
            while (iter.hasNext())
            {
                PublishMessage message = iter.next();
                if (message.getMessageID() == messageID)
                {
                    iter.remove();
                    final PubCompMessage pubCompMessage = new PubCompMessage();
                    pubCompMessage.setMessageID(messageID);
                    write(MqttCodec.encode(pubCompMessage));
                    callback.messageArrived(message);
                    return;
                }
            }
            // TODO oops couldn't find the message
        }
        else
        {
            // ignore message
        }
    }

    void handle(PubAckMessage pubAckMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            int messageID = pubAckMessage.getMessageID();
            ListIterator<PublishMessage> iter = inflightOutboundQoS1MessagesPuback.listIterator();
            while (iter.hasNext())
            {
                PublishMessage message = iter.next();
                if (message.getMessageID() == messageID)
                {
                    iter.remove();
                    // TODO: callback.messageDelivered(message)
                    return;
                }
            }
        }
        else {
            // ignore message
        }
    }

    void handle(PubRecMessage pubRecMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            int messageID = pubRecMessage.getMessageID();
            ListIterator<PublishMessage> iter = inflightOutboundQoS2MessagesPubrec.listIterator();
            while (iter.hasNext())
            {
                PublishMessage message = iter.next();
                if (message.getMessageID() == messageID)
                {
                    iter.remove();
                    final PubRelMessage pubRelMessage = new PubRelMessage();
                    pubRelMessage.setMessageID(messageID);
                    write(MqttCodec.encode(pubRelMessage));
                    inflightOutboundQoS2MessagesPubcomp.add(message);
                    return;
                }
            }
        }
        else {
            // ignore message
        }
    }

    void handle(PubCompMessage pubCompMessage)
    {
        if (topLevelState == TopLevelState.EXPECT_MESSAGE) {
            int messageID = pubCompMessage.getMessageID();
            ListIterator<PublishMessage> iter = inflightOutboundQoS2MessagesPubcomp.listIterator();
            while (iter.hasNext())
            {
                PublishMessage message = iter.next();
                if (message.getMessageID() == messageID)
                {
                    iter.remove();
                    return;
                }
            }
        }
        else {
            // ignore message
        }
    }

    void write(Buffer buffer)
    {
        if (netSocket.writeQueueFull()) {
            netSocket.pause(); // pauses input, drain handler above will be called when ready
            netSocket.drainHandler(new VoidHandler() {
                @Override
                protected void handle() {
                    netSocket.resume();
                }
            });
        }
        lastCommunicationMs = System.currentTimeMillis();
        netSocket.write(buffer);
        if (pingTimerID != 0) { vertx.cancelTimer(pingTimerID); pingTimerID = 0; }
        if (keepaliveMillis > 0) {
            pingTimerID = vertx.setTimer(keepaliveMillis, new Handler<Long>() {
                @Override
                public void handle(Long event) {
                final PingReqMessage message = new PingReqMessage();
                write(MqttCodec.encode(message));
                }
            });
        }
    }

    public void subscribe(List<MqttSubscription> subscriptions)
    {
        final SubscribeMessage subscribeMessage = new SubscribeMessage();
        subscribeMessage.setMessageID(new Integer(outboundMID++));
        subscribeMessage.setQos(AbstractMessage.QOSType.LEAST_ONE); // qos type 1
        for (MqttSubscription subscription : subscriptions)
            subscribeMessage.addSubscription(new SubscribeMessage.Couple(subscription.qos, subscription.topic));
        write(MqttCodec.encode(subscribeMessage));
    }

    public void unsubscribe(List<String> topics)
    {
        final UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage();
        unsubscribeMessage.setMessageID(new Integer(outboundMID++));
        unsubscribeMessage.setQos(AbstractMessage.QOSType.LEAST_ONE); // qos type 1
        for (String topic : topics)
            unsubscribeMessage.addTopicFilter(topic);
        write(MqttCodec.encode(unsubscribeMessage));
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

    public void disconnect()
    {
        final DisconnectMessage disconnectMessage = new DisconnectMessage();
        write(MqttCodec.encode(disconnectMessage));
        cleanDisconnect = true;
        netSocket.close();
    }
}
