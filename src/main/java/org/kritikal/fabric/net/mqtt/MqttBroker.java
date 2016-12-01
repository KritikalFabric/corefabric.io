package org.kritikal.fabric.net.mqtt;

import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.MapStoreFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.net.mqtt.codec.EncodePublish;
import org.kritikal.fabric.net.mqtt.entities.AbstractMessage;
import org.kritikal.fabric.net.mqtt.entities.ConnectMessage;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;
import org.kritikal.fabric.core.VERTXDEFINES;
import org.kritikal.fabric.net.CNetSocket;
import org.kritikal.fabric.net.CServerWebSocket;
import org.kritikal.fabric.net.CSockJSSocket;
import org.kritikal.fabric.net.KillConnectionError;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.handler.sockjs.SockJSSocket;
import io.vertx.core.net.NetSocket;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Created by ben on 8/26/14.
 */
public class MqttBroker implements IMqttServerCallback, IMqttBroker {

    final public Vertx vertx;
    final Logger logger;

    final IMap<String, PublishMessage> retainedCluster = CoreFabric.getHazelcastInstance().getMap("mqtt.retained");
    final ConcurrentMap<String, PublishMessage> retainedLocal = new ConcurrentHashMap<>();
    final ITopic<PublishMessage> hazelcastTopic = CoreFabric.getHazelcastInstance().getTopic("mqtt.enqueue");

    public MqttBroker(Vertx vertx) {
        this.vertx = vertx;
        this.logger = LoggerFactory.getLogger(getClass());
        hazelcastTopic.addMessageListener(m -> {
            final PublishMessage publishMessage = m.getMessageObject();
            if (publishMessage.origin.equals(CoreFabric.ServerConfiguration.instance)) return;
            enqueue(null, publishMessage);
        });
        vertx.setPeriodic(9997l, l -> {
            // Announce our life; across the cluster...
            final String json = CoreFabric.globalConfig.encode();
            try { MqttBrokerVerticle.mqttBroker().apiPublish("$cf/"+CoreFabric.ServerConfiguration.hostname + "/|g", json.getBytes("UTF-8"), 2, false, 13997); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
        });
        _messageArrivedThread.setPriority(Thread.MAX_PRIORITY);
        _messageArrivedThread.start();
        _messageForBroadcastThread.setPriority(Thread.MAX_PRIORITY);
        _messageForBroadcastThread.start();
        _retainedThread.setPriority(Thread.MIN_PRIORITY);
        _retainedThread.start();
        _retainedClusterThread.setPriority(Thread.MIN_PRIORITY);
        _retainedClusterThread.start();
    }

    final public static class ContentHelper {
        JsonObject jsonified = null;
        boolean extracted = false;
        byte[] payload = null;
        MessageEncapsulation encapulated = null;
    }

    final public static class MyMqttServerProtocol extends MqttServerProtocol
    {
        public MyMqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final NetSocket netSocket)
        {
            super(logger, vertx, callback, new CNetSocket(netSocket));
        }

        public MyMqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final ServerWebSocket webSocket, final String corefabric)
        {
            super(logger, vertx, callback, new CServerWebSocket(webSocket, corefabric));
        }

        public MyMqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final SockJSSocket sockJSSocket)
        {
            super(logger, vertx, callback, new CSockJSSocket(sockJSSocket));
        }

        public String clientID = null;
        public MyMqttState state = null;

        @Override
        public void publish(String topic, byte[] payload, int qos, boolean retain) {
            if (DEBUG && VERBOSE) {
                logger.debug("Publishing " + topic + " to " + clientID);
            }
            super.publish(topic, payload, qos, retain);
        }
    }

    final public static class MyMqttState
    {
        public MyMqttState(final String clientID) { this.clientID = clientID; }
        public final String clientID;
        public ConcurrentLinkedQueue<MqttSubscription> subscriptions = new ConcurrentLinkedQueue<>();
        public ConcurrentLinkedQueue<PublishMessage> queued = null;
        public boolean willRetain = false;
        public String willTopic = null;
        public String willMessage = null;
        public byte willQos = 0;
    }

    final public static class InternalConnection
    {
        public InternalConnection(String topic, String endPoint) {
            this.subscription = new MqttSubscription(topic, (byte)2);
            this.endPoint = endPoint;
        }
        public InternalConnection(String topic, java.util.function.Consumer<MessageEncapsulation> onMessage) {
            this.subscription = new MqttSubscription(topic, (byte)2);
            this.onMessage = onMessage;
        }
        public final MqttSubscription subscription;
        public String endPoint = null;
        public java.util.function.Consumer<MessageEncapsulation> onMessage = null;
    }

    public ConcurrentLinkedQueue<MyMqttServerProtocol> waitingForConnect = new ConcurrentLinkedQueue<>();
    protected ConcurrentLinkedQueue<MyMqttServerProtocol> connected = new ConcurrentLinkedQueue<>();
    protected ConcurrentHashMap<String, MyMqttState> disconnected = new ConcurrentHashMap<>();
    protected ConcurrentLinkedQueue<InternalConnection> internal = new ConcurrentLinkedQueue<>();


    // IMqttServerCallback

    public void connected(MqttServerProtocol protocol, ConnectMessage connectMessage)
    {
        if (DEBUG) {
            protocol.logger.debug("Client connected " + connectMessage.getClientID());
        }
        if (!waitingForConnect.remove(protocol)) {
            throw new KillConnectionError();
        }

        final MyMqttServerProtocol current = ((MyMqttServerProtocol)protocol);
        current.clientID = connectMessage.getClientID();

        // basic security
        if (current.socket instanceof CServerWebSocket) {
            final CServerWebSocket socket = (CServerWebSocket)current.socket;
            if (!current.clientID.startsWith("corefabric--"+socket.corefabric+"--")) {
                throw new KillConnectionError();
            }
        }

        connected.forEach(p -> {
            if (current.clientID.equals(p.clientID)) {
                connected.remove(p);
                p.forceDisconnect();
            }
        });

        connected.add(current);

        if (connectMessage.isCleanSession())
        {
            MyMqttState myMqttState = new MyMqttState(current.clientID);
            disconnected.remove(current.clientID);
            current.state = myMqttState;
        }
    }

    public void postConnAck(MqttServerProtocol protocol, ConnectMessage connectMessage)
    {
        boolean publishQueuedAndRetained = false;
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;

        if (!connectMessage.isCleanSession()) {
            myMqttServerProtocol.state = disconnected.getOrDefault(myMqttServerProtocol.clientID, null);
            if (myMqttServerProtocol.state == null) {
                MyMqttState myMqttState = new MyMqttState(myMqttServerProtocol.clientID);
                myMqttServerProtocol.state = myMqttState;
            } else
                publishQueuedAndRetained = true;
        }

        if (connectMessage.isWillFlag()) {
            this.setWill(protocol, connectMessage.isWillRetain(), connectMessage.getWillTopic(), new String(connectMessage.getWillMessage()), connectMessage.getWillQos());
        }
        else {
            this.setWill(protocol, false, null, null, (byte)0);
        }

        if (publishQueuedAndRetained) {
            // publish queued
            {
                if (myMqttServerProtocol.state.queued != null)
                {
                    myMqttServerProtocol.state.queued.forEach(publishMessage -> {
                        byte[] payload = null;

                        boolean match = false;
                        byte qos = 0;

                        for (MqttSubscription subscription : myMqttServerProtocol.state.subscriptions)
                        {
                            if (subscription.matches(publishMessage.getTopic())) {
                                match = true;
                                qos = qos > subscription.qos ? qos : subscription.qos;
                                if (qos == 2) break;
                            }
                        }

                        if (match) {
                            ByteBuffer payloadBuffer = publishMessage.getPayload();
                            payload = payloadBuffer == null ? null : payloadBuffer.array();
                            myMqttServerProtocol.publish(publishMessage.getTopicName(), payload, qos, publishMessage.isRetainFlag());
                        }
                    });
                    myMqttServerProtocol.state.queued = null;
                }
            }

            publishRetained(myMqttServerProtocol, myMqttServerProtocol.state.subscriptions);
        }
    }

    @Override
    public void apiPurge()
    {
        if (DEBUG && VERBOSE) {
            logger.debug("(API) purge");
        }
        retainedLocal.clear();
    }

    @Override
    public void apiPublish(String topic, byte[] body, int qos, boolean retained)
    {
        if (DEBUG && VERBOSE) {
            logger.debug("(API) publish " + topic);
        }

        ByteBuffer buffer = body != null ? ByteBuffer.wrap(body) : null;
        PublishMessage message = new PublishMessage();
        message.setTopicName(topic);
        message.setPayload(buffer);
        message.setQos(qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : (qos == 1 ? AbstractMessage.QOSType.MOST_ONE : AbstractMessage.QOSType.LEAST_ONE));
        message.setRetainFlag(retained);

        messageArrived(null, message);
    }

    @Override
    public void apiPublish(String topic, byte[] body, int qos, boolean retained, long ttl)
    {
        if (DEBUG && VERBOSE) {
            logger.debug("(API) publish " + topic);
        }

        ByteBuffer buffer = body != null ? ByteBuffer.wrap(body) : null;
        PublishMessage message = new PublishMessage();
        message.setTopicName(topic);
        message.setPayload(buffer);
        message.setQos(qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : (qos == 1 ? AbstractMessage.QOSType.MOST_ONE : AbstractMessage.QOSType.LEAST_ONE));
        message.setRetainFlag(retained);

        if (ttl > 0l) message.expires = ttl + new java.util.Date().getTime();

        messageArrived(null, message);
    }

    public final static class MessageEncapsulation {
        public MessageEncapsulation(final String topic, final byte[] payload) {
            this.topic = topic;
            this.payload = payload;
        }
        public final String topic;
        public final byte[] payload;
    }

    @Override
    public void apiSubscribe(String topic, String endPoint)
    {
        if (DEBUG) {
            logger.debug("(API) subscribe " + topic + " --> " + endPoint);
        }

        for (InternalConnection i : internal) {
            if (i.subscription.topic.equals(topic) && i.endPoint != null && i.endPoint.equals(endPoint))
                return;
        }
        internal.add(new InternalConnection(topic, endPoint));
    }

    @Override
    public void apiSubscribe(String topic, Consumer<MessageEncapsulation> onMessage)
    {
        if (DEBUG) {
            logger.debug("(API) subscribe " + topic + " --> callback");
        }

        for (InternalConnection i : internal) {
            if (i.subscription.topic.equals(topic) && i.onMessage != null && i.onMessage == onMessage)
                return;
        }
        internal.add(new InternalConnection(topic, onMessage));
    }

    @Override
    public void apiUnsubscribe(String topic, String endPoint)
    {
        if (DEBUG) {
            logger.debug("(API) unsubscribe " + topic + " --> " + endPoint);
        }

        ArrayList<InternalConnection> toRemove = new ArrayList<>();
        internal.forEach(i -> {
            if (i.subscription.topic.equals(topic) && i.endPoint != null && i.endPoint.equals(endPoint))
                toRemove.add(i);
        });
        for (InternalConnection ic : toRemove) internal.remove(ic);
    }

    @Override
    public void apiUnsubscribe(String topic, Consumer<MessageEncapsulation> onMessage)
    {
        if (DEBUG) {
            logger.debug("(API) unsubscribe " + topic + " --> callback");
        }

        ArrayList<InternalConnection> toRemove = new ArrayList<>();
        internal.forEach(i -> {
            if (i.subscription.topic.equals(topic) && i.onMessage != null && i.onMessage == onMessage)
                toRemove.add(i);
        });
        for (InternalConnection ic : toRemove) internal.remove(ic);
    }

    @Override
    public JsonObject apiPeek(String topic)
    {
        if (DEBUG && VERBOSE) {
            logger.debug("(API) peek " + topic);
        }

        JsonObject ret = null;

        MqttTopic topic1 = new MqttTopic(topic);
        PublishMessage publishMessage = null;

        if (clusterWide(topic1)) {
            publishMessage = retainedCluster.get(topic);
        } else {
            publishMessage = retainedLocal.get(topic);
        }

        if (publishMessage != null) {
            ByteBuffer payloadBuffer = publishMessage.getPayload();
            byte[] payload = payloadBuffer == null ? null : payloadBuffer.array();

            ret = new JsonObject();
            ret.put("topic", publishMessage.getTopicName());
            if (payload != null) ret.put("body", payload);
            ret.put("qos", 0);
            ret.put("retain", true);
        }

        return ret;
    }

    @Override
    public void apiBroadcast(String topic, byte[] body, int qos, boolean retained)
    {
        if (DEBUG && VERBOSE) {
            logger.debug("(API) broadcast " + topic);
        }

        ByteBuffer buffer = body != null ? ByteBuffer.wrap(body) : null;
        PublishMessage message = new PublishMessage();
        message.setTopicName(topic);
        message.setPayload(buffer);
        message.setQos(qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : (qos == 1 ? AbstractMessage.QOSType.MOST_ONE : AbstractMessage.QOSType.LEAST_ONE));
        message.setRetainFlag(retained);

        messageForBroadcast(message);
    }

    public void messageForBroadcast(PublishMessage publishMessage) {
        _messageForBroadcastQ.add(publishMessage);
    }

    private final Thread _retainedThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            long now = new java.util.Date().getTime();
            final ConcurrentLinkedQueue<String> toRemove = new ConcurrentLinkedQueue<>();
            retainedLocal.forEach((key, publishMessage) -> {
                if (publishMessage.expires != 0l)
                    if (publishMessage.expires < now) {
                        toRemove.add(key);
                    }
            });
            for (String key : toRemove) retainedLocal.remove(key);
            if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
        }
    });

    private final Thread _retainedClusterThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            long now = new java.util.Date().getTime();
            final ConcurrentLinkedQueue<String> toRemove = new ConcurrentLinkedQueue<>();
            retainedCluster.forEach((key, publishMessage) -> {
                if (publishMessage.expires != 0l)
                    if (publishMessage.expires < now) {
                        toRemove.add(key);
                    }
            });
            for (String key : toRemove) retainedCluster.remove(key);
            try { Thread.sleep(9997l); } catch (InterruptedException ie) { }
        }
    });

    private final ConcurrentLinkedQueue<PublishMessage> _messageForBroadcastQ = new ConcurrentLinkedQueue<>();
    private final Thread _messageForBroadcastThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            try {
                PublishMessage publishMessage = null;
                while ((publishMessage = _messageForBroadcastQ.poll()) != null)
                    _messageForBroadcast(publishMessage);
                if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
            }
            catch (Throwable t) {
                LoggerFactory.getLogger(MqttBroker.class).fatal("", t);
            }
        }
    });

    public final void _messageForBroadcast(PublishMessage publishMessage) {
        final ContentHelper contentHelper = new ContentHelper();
        MqttSubscription topicMatcher = new MqttSubscription(publishMessage.getTopicName(), (byte)0);

        /* cannot retain broadcast messages
        if (publishMessage.isRetainFlag())
        {
            ByteBuffer payload = publishMessage.getPayload();
            if (payload == null || payload.capacity() == 0)
            {
                retained.remove(publishMessage.getTopicName());
            }
            else
                retained.put(publishMessage.getTopicName(), publishMessage);
        }
        */

        // message arrived, send out to (all) subscribers
        internal.forEach(i -> {
            if (i.subscription.isWildcard()) return;
            if (topicMatcher.matches(i.subscription)) {
                if (i.endPoint != null) {
                    if (contentHelper.jsonified == null) {
                        if (!contentHelper.extracted) {
                            ByteBuffer payloadBuffer = publishMessage.getPayload();
                            contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                            contentHelper.extracted = true;
                        }
                        contentHelper.jsonified = new JsonObject();
                        if (contentHelper.payload != null)
                            contentHelper.jsonified.put("body", contentHelper.payload);
                        contentHelper.jsonified.put("qos", 2);
                        contentHelper.jsonified.put("retain", publishMessage.isRetainFlag());
                    }
                    contentHelper.jsonified.put("topic", publishMessage.getTopicName());
                    vertx.eventBus().send(i.endPoint, contentHelper.jsonified, VERTXDEFINES.DELIVERY_OPTIONS);
                } else if (i.onMessage != null) {
                    if (contentHelper.encapulated == null) {
                        if (!contentHelper.extracted) {
                            ByteBuffer payloadBuffer = publishMessage.getPayload();
                            contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                            contentHelper.extracted = true;
                        }
                        contentHelper.encapulated = new MessageEncapsulation(publishMessage.getTopicName(), contentHelper.payload);
                    }
                    try {
                        i.onMessage.accept(contentHelper.encapulated);
                    }
                    catch (Throwable t) {
                        logger.error(i.subscription.toString(), t);
                    }
                } else {
                    throw new FabricError();
                }
            }
        });

        connected.forEach(myMqttServerProtocol -> {
            myMqttServerProtocol.state.subscriptions.forEach(subscription -> {
                if (subscription == null) return;
                if (subscription.isWildcard()) return;
                if (topicMatcher.matches(subscription)) {
                    if (!contentHelper.extracted) {
                        ByteBuffer payloadBuffer = publishMessage.getPayload();
                        contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                        contentHelper.extracted = true;
                    }
                    myMqttServerProtocol.publish(subscription.topic, contentHelper.payload, (byte)0, publishMessage.isRetainFlag());
                }
            });
        });

        // note cannot retain broadcast messages
    }

    public class PublishHelper
    {
        public boolean match = false;
        public byte qos = 0;
    }

    public static boolean clusterWide(MqttTopic topic) {
        final String[] parts = topic.parts;
        final String root = parts[0];
        int l = root.length();
        final char chRoot = l > 0 ? root.charAt(0) : 0;
        if (chRoot=='|') {
            return true;
        }
        if (parts.length > 1) {
            final String leaf = parts[parts.length - 1];
            l = leaf.length();
            final char chLeaf = l > 0 ? leaf.charAt(0) : 0;
            if (chLeaf == '|') {
                return true;
            }
        }
        return false;
    }

    public static boolean clusterWide(PublishMessage publishMessage) {
        return clusterWide(publishMessage.getTopic());
    }

    public void publishRetained(MqttServerProtocol protocol, ConcurrentLinkedQueue<MqttSubscription> newSubscriptions)
    {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;
        retainedLocal.forEach((k, publishMessage) -> {
            byte[] payload = null;

            final PublishHelper prh = new PublishHelper();

            newSubscriptions.forEach(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    prh.match = true;
                    prh.qos = prh.qos > subscription.qos ? prh.qos : subscription.qos;
                }
            });

            if (prh.match) {
                ByteBuffer payloadBuffer = publishMessage.getPayload();
                payload = payloadBuffer == null ? null : payloadBuffer.array();
                myMqttServerProtocol.publish(publishMessage.getTopicName(), payload, prh.qos, publishMessage.isRetainFlag());
            }
        });
        retainedCluster.forEach((k, publishMessage) -> {
            byte[] payload = null;

            final PublishHelper prh = new PublishHelper();

            newSubscriptions.forEach(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    prh.match = true;
                    prh.qos = prh.qos > subscription.qos ? prh.qos : subscription.qos;
                }
            });

            if (prh.match) {
                ByteBuffer payloadBuffer = publishMessage.getPayload();
                payload = payloadBuffer == null ? null : payloadBuffer.array();
                myMqttServerProtocol.publish(publishMessage.getTopicName(), payload, prh.qos, publishMessage.isRetainFlag());
            }
        });
    }

    public final static class PM {
        public PM(MqttServerProtocol p, PublishMessage m) {
            this.p = p;
            this.m = m;
        }
        public MqttServerProtocol p;
        public PublishMessage m;
    }
    private final ConcurrentLinkedQueue<PM> _messageArrivedQ = new ConcurrentLinkedQueue<>();
    private final Thread _messageArrivedThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            try {
                PM pm = null;
                while ((pm = _messageArrivedQ.poll()) != null)
                    _messageArrived(pm.p, pm.m);
                if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
            }
            catch (Throwable t) {
                LoggerFactory.getLogger(MqttBroker.class).fatal("", t);
            }
        }
    });

    public void messageArrived(MqttServerProtocol protocol, PublishMessage publishMessage) {
        if (clusterWide(publishMessage)) {
            publishMessage.origin = CoreFabric.ServerConfiguration.instance;
            /*
            ByteBuf buf = Unpooled.buffer();
            EncodePublish.encode(publishMessage, buf, true);
            vertx.eventBus().publish("mqtt.enqueue", Buffer.buffer(buf)); // cluster-wide broadcast
            */
            hazelcastTopic.publish(publishMessage);
        }
        enqueue(protocol, publishMessage);
    }

    public void enqueue(MqttServerProtocol protocol, PublishMessage publishMessage) {
        _messageArrivedQ.add(new PM(protocol, publishMessage));
    }

    public void _messageArrived(MqttServerProtocol protocol, PublishMessage publishMessage)
    {
        if (DEBUG && VERBOSE) {
            logger.debug("Message arrived " + publishMessage.getTopicName());
        }

        if (publishMessage.isRetainFlag()) {
            ByteBuffer payload = publishMessage.getPayload();
            if (clusterWide(publishMessage)) {
                if (payload == null || payload.capacity() == 0) {
                    retainedCluster.remove(publishMessage.getTopicName());
                } else
                    retainedCluster.put(publishMessage.getTopicName(), publishMessage);
            } else {
                if (payload == null || payload.capacity() == 0) {
                    retainedLocal.remove(publishMessage.getTopicName());
                } else
                    retainedLocal.put(publishMessage.getTopicName(), publishMessage);
            }
        }

        final ContentHelper contentHelper = new ContentHelper();

        // message arrived, send out to (all) subscribers
        internal.forEach(i -> {
            if (i.subscription.matches(publishMessage.getTopic())) {
                if (i.endPoint != null) {
                    if (contentHelper.jsonified == null) {
                        if (!contentHelper.extracted) {
                            ByteBuffer payloadBuffer = publishMessage.getPayload();
                            contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                            contentHelper.extracted = true;
                        }
                        contentHelper.jsonified = new JsonObject();
                        if (contentHelper.payload != null)
                            contentHelper.jsonified.put("body", contentHelper.payload);
                        contentHelper.jsonified.put("qos", 2);
                        contentHelper.jsonified.put("retain", publishMessage.isRetainFlag());
                    }
                    contentHelper.jsonified.put("topic", publishMessage.getTopicName());
                    vertx.eventBus().send(i.endPoint, contentHelper.jsonified, VERTXDEFINES.DELIVERY_OPTIONS);
                } else if (i.onMessage != null) {
                    if (contentHelper.encapulated == null) {
                        if (!contentHelper.extracted) {
                            ByteBuffer payloadBuffer = publishMessage.getPayload();
                            contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                            contentHelper.extracted = true;
                        }
                        contentHelper.encapulated = new MessageEncapsulation(publishMessage.getTopicName(), contentHelper.payload);
                    }
                    try {
                        i.onMessage.accept(contentHelper.encapulated);
                    }
                    catch (Throwable t) {
                        logger.error(i.subscription.toString(), t);
                    }
                } else {
                    throw new FabricError();
                }
            }
        });

        connected.forEach(myMqttServerProtocol -> {
            if (protocol != null) {
                if (protocol.noEcho && myMqttServerProtocol == protocol) return; // used for bridges
            }

            if (myMqttServerProtocol == null) return; // can't help you
            if (myMqttServerProtocol.state == null) return;
            if (myMqttServerProtocol.state.subscriptions == null) return;

            final PublishHelper ph = new PublishHelper();

            myMqttServerProtocol.state.subscriptions.forEach(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    ph.match = true;
                    ph.qos = ph.qos > subscription.qos ? ph.qos : subscription.qos;
                }
            });

            if (ph.match) {
                if (!contentHelper.extracted) {
                    ByteBuffer payloadBuffer = publishMessage.getPayload();
                    contentHelper.payload = payloadBuffer == null ? null : payloadBuffer.array();
                    contentHelper.extracted = true;
                }
                myMqttServerProtocol.publish(publishMessage.getTopicName(), contentHelper.payload, ph.qos, publishMessage.isRetainFlag());
            }
        });

        // queue non-retained messages qos 1 or 2 only?
        if (!publishMessage.isRetainFlag() /* && publishMessage.getQos().ordinal() > 0 */) {
            disconnected.forEach((k, state) -> {
                final PublishHelper ph = new PublishHelper();
                state.subscriptions.forEach(subscription -> {
                    if (subscription.matches(publishMessage.getTopic())) {
                        ph.match = true;
                        ph.qos = ph.qos > subscription.qos ? ph.qos : subscription.qos;
                    }
                });
                if (ph.match) {
                    if (state.queued == null) state.queued = new ConcurrentLinkedQueue<>();
                    state.queued.add(publishMessage);
                }
            });
        }
    }
    public boolean authorize(String username, byte[] password)
    {
        return false;
    }
    void setWill(MqttServerProtocol protocol, boolean willRetain, String willTopic, String willMessage, byte willQos)
    {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol)protocol;
        myMqttServerProtocol.state.willRetain = willRetain;
        myMqttServerProtocol.state.willTopic = willTopic;
        myMqttServerProtocol.state.willMessage = willMessage;
        myMqttServerProtocol.state.willQos = willQos;
    }

    public byte subscribe(MqttServerProtocol protocol, MqttSubscription subscription) {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;
        if (DEBUG) {
            protocol.logger.debug("Subscribe message " + subscription.topic + " from " + myMqttServerProtocol.clientID);
        }

        vertx.executeBlocking(f -> {
            myMqttServerProtocol.state.subscriptions.forEach(s -> {
                if (s.topic.equals(subscription.topic))
                    myMqttServerProtocol.state.subscriptions.remove(s);
            });

            myMqttServerProtocol.state.subscriptions.add(subscription);
        }, false, r -> {});

        return subscription.qos;
    }

    public void unsubscribe(MqttServerProtocol protocol, String topic)
    {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;

        vertx.executeBlocking(f -> {
            myMqttServerProtocol.state.subscriptions.forEach(s -> {
                if (s.topic.equals(topic))
                    myMqttServerProtocol.state.subscriptions.remove(s);
            });
        }, false, r -> {});
    }

    public void disconnected(MqttServerProtocol protocol)
    {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;
        if (DEBUG) {
            protocol.logger.debug("Disconnected " + myMqttServerProtocol.clientID);
        }

        vertx.executeBlocking(f -> {

            if (myMqttServerProtocol.clientID != null && !"".equals(myMqttServerProtocol.clientID)) {
                disconnected.put(myMqttServerProtocol.clientID, myMqttServerProtocol.state);
                connected.remove(myMqttServerProtocol);

                if (!myMqttServerProtocol.cleanDisconnect && myMqttServerProtocol.state != null &&
                        myMqttServerProtocol.state.willTopic != null && myMqttServerProtocol.state.willMessage != null) {
                    PublishMessage willMessage = new PublishMessage();
                    willMessage.setMessageID(new Integer(protocol.outboundMID++));
                    switch (myMqttServerProtocol.state.willQos) {
                        case 0:
                            willMessage.setQos(AbstractMessage.QOSType.MOST_ONE);
                            break;
                        case 1:
                            willMessage.setQos(AbstractMessage.QOSType.LEAST_ONE);
                            break;
                        case 2:
                            willMessage.setQos(AbstractMessage.QOSType.EXACTLY_ONCE);
                            break;
                    }
                    willMessage.setRetainFlag(myMqttServerProtocol.state.willRetain);
                    willMessage.setTopicName(myMqttServerProtocol.state.willTopic);
                    byte[] data = null;
                    try {
                        data = myMqttServerProtocol.state.willMessage.getBytes("UTF-8");
                    } catch (Exception ex) { /* should never happen */ }
                    willMessage.setPayload(ByteBuffer.wrap(data));
                    messageArrived(protocol, willMessage);
                }
            }

        }, false, r -> {});
    }
}
