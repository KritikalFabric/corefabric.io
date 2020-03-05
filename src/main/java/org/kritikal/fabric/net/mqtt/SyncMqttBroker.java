package org.kritikal.fabric.net.mqtt;

import com.datastax.driver.core.utils.UUIDs;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.net.http.CFCookie;
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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ben on 8/26/14.
 */
public class SyncMqttBroker implements IMqttServerCallback, ISyncMqttBroker {

    final public Vertx vertx;
    final Logger logger;

    final IMap<String, PublishMessage> retainedCluster = CoreFabric.getHazelcastInstance().getMap("mqtt.retained");
    final ConcurrentMap<String, PublishMessage> retainedLocal = new ConcurrentHashMap<>();
    final ITopic<PublishMessage> hazelcastTopic = CoreFabric.getHazelcastInstance().getTopic("mqtt.enqueue");

    public SyncMqttBroker(Vertx vertx) {
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
            try { MqttBrokerVerticle.syncBroker().syncApiPublish("$cf/"+CoreFabric.ServerConfiguration.hostname + "/|g", json.getBytes("UTF-8"), 2, false, 13997); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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
        private final Function<Void, ByteBuffer> provideBuffer;
        private final Function<Void, Boolean> provideRetain;
        private final Function<Void, String> provideTopicName;

        public ContentHelper(Function<Void, ByteBuffer> provideBuffer, Function<Void, Boolean> provideRetain, Function<Void, String> provideTopicName) {
            this.provideBuffer = provideBuffer;
            this.provideRetain = provideRetain;
            this.provideTopicName = provideTopicName;
        }

        private final AtomicReference<byte[]> payload = new AtomicReference<>(null);
        private final AtomicReference<JsonObject> jsonified = new AtomicReference<>(null);
        private final AtomicReference<MessageEncapsulation> msg = new AtomicReference<>(null);

        public byte[] payload() {
            return this.payload.updateAndGet(ary ->  {
                if (ary == null) {
                    final ByteBuffer byteBuffer = this.provideBuffer.apply(null);
                    if (byteBuffer != null) {
                        ary = byteBuffer.array();
                    }
                    if (ary == null) {
                        ary = new byte[0];
                    }
                }
                return ary;
            });
        }

        public JsonObject json() {
            return this.jsonified.updateAndGet(j -> {
                if (j == null) {
                    j = new JsonObject();
                    j.put("body", payload());
                    j.put("qos", 2);
                    j.put("retain", provideRetain.apply(null));
                    j.put("topic", provideTopicName.apply(null));
                }
                return j;
            });
        }

        public MessageEncapsulation encapsulated() {
            return this.msg.updateAndGet(me -> {
                if (me == null) {
                    me = new MessageEncapsulation(provideTopicName.apply(null), payload());
                }
                return me;
            });
        }
    }

    final public static class MyMqttServerProtocol extends MqttServerProtocol
    {
        public MyMqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final NetSocket netSocket)
        {
            super(logger, vertx, callback, new CNetSocket(netSocket));
        }

        public MyMqttServerProtocol(final Logger logger, final Vertx vertx, final IMqttServerCallback callback, final ServerWebSocket webSocket, final CFCookie corefabric)
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
                logger.info("Publishing " + topic + " to " + clientID);
            }
            super.publish(topic, payload, qos, retain);
        }
    }

    final public static class MyMqttState
    {
        public MyMqttState(final String clientID) { this.clientID = clientID; }
        public final String clientID;
        public ConcurrentLinkedQueue<MqttSubscription> subscriptions = new ConcurrentLinkedQueue<>();
        private AtomicReference<ConcurrentLinkedQueue<PublishMessage>> queued = new AtomicReference<>(null);
        public ConcurrentLinkedQueue<PublishMessage> queue() {
            return queued.updateAndGet(q -> {
                ConcurrentLinkedQueue<PublishMessage> r = q;
                if (q != null) {
                    q = null;
                }
                return r;
            });
        }
        public void enqueue(PublishMessage publishMessage) {
            queued.updateAndGet(q -> {
                if (q == null) q = new ConcurrentLinkedQueue<PublishMessage>();
                return q;
            }).add(publishMessage);
        }
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
            protocol.logger.info("Client connected " + connectMessage.getClientID());
        }
        if (!waitingForConnect.remove(protocol)) {
            throw new KillConnectionError();
        }

        final MyMqttServerProtocol current = ((MyMqttServerProtocol)protocol);
        current.clientID = connectMessage.getClientID();

        // basic security, disabled on development, ensure the clientID contains the cookie
        /*
        if (!CoreFabric.ServerConfiguration.nodejsDev) {
            if (current.socket instanceof CServerWebSocket) {
                final CServerWebSocket socket = (CServerWebSocket) current.socket;
                if (!current.clientID.startsWith("corefabric--" + socket.corefabric.session_uuid.toString() + "--")) {
                    throw new KillConnectionError();
                }
            }
        }
         see #121
         */
        if (current.socket instanceof  CServerWebSocket) {
            current.clientID = "corefabric--" + ((CServerWebSocket) current.socket).corefabric.session_uuid.toString() + "--" + UUIDs.timeBased().toString();
        }

        connected.stream().mapToLong(p -> {
            if (current.clientID.equals(p.clientID)) {
                connected.remove(p);
                p.forceDisconnect();
                return 1l;
            }
            return 0l;
        }).sum();

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
                ConcurrentLinkedQueue<PublishMessage> queued = myMqttServerProtocol.state.queue();
                if (queued != null)
                {
                    queued.stream().mapToLong(publishMessage -> {
                        final ContentHelper ch = new ContentHelper(v -> publishMessage.getPayload(), v -> publishMessage.isRetainFlag(), v -> publishMessage.getTopicName());
                        final QosHelper qh = new QosHelper();

                        myMqttServerProtocol.state.subscriptions.stream().mapToLong(subscription -> {
                            if (subscription.matches(publishMessage.getTopic())) {
                                qh.qos(subscription.qos);
                                return 1l;
                            }
                            return 0l;
                        }).anyMatch(l -> qh.terminated());

                        if (qh.isMatch()) {
                            myMqttServerProtocol.publish(publishMessage.getTopicName(), ch.payload(), qh.getQos(), publishMessage.isRetainFlag());
                        }

                        return 1l;
                    }).sum();
                }
            }

            publishRetained(myMqttServerProtocol, myMqttServerProtocol.state.subscriptions);
        }
    }

    @Override
    public void syncApiPurge()
    {
        if (DEBUG && VERBOSE) {
            logger.info("(API) purge");
        }
        retainedLocal.clear();
    }

    @Override
    public void syncApiPublish(String topic, byte[] body, int qos, boolean retained)
    {
        if (DEBUG && VERBOSE) {
            logger.info("(API) publish " + topic);
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
    public void syncApiPublish(String topic, byte[] body, int qos, boolean retained, long ttl)
    {
        if (DEBUG && VERBOSE) {
            logger.info("(API) publish " + topic);
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
    public void syncApiSubscribe(String topic, String endPoint)
    {
        if (DEBUG) {
            logger.info("(API) subscribe " + topic + " --> " + endPoint);
        }

        if (internal.stream().mapToLong(i -> {
            return (i.subscription.topic.equals(topic) && i.endPoint != null && i.endPoint.equals(endPoint)) ? 1l : 0l;
        }).sum() > 0) return;
        internal.add(new InternalConnection(topic, endPoint));
    }

    @Override
    public void syncApiSubscribe(String topic, Consumer<MessageEncapsulation> onMessage)
    {
        if (DEBUG) {
            logger.info("(API) subscribe " + topic + " --> callback");
        }
        if (internal.stream().mapToLong(i -> {
            return (i.subscription.topic.equals(topic) && i.endPoint != null && i.onMessage == onMessage) ? 1l : 0l;
        }).sum() > 0) return;
        internal.add(new InternalConnection(topic, onMessage));
    }

    @Override
    public void syncApiUnsubscribe(String topic, String endPoint)
    {
        if (DEBUG) {
            logger.info("(API) unsubscribe " + topic + " --> " + endPoint);
        }

        internal.removeIf(i -> i.subscription.topic.equals(topic) && i.endPoint != null && i.endPoint.equals(endPoint));
    }

    @Override
    public void syncApiUnsubscribe(String topic, Consumer<MessageEncapsulation> onMessage)
    {
        if (DEBUG) {
            logger.info("(API) unsubscribe " + topic + " --> callback");
        }

        internal.removeIf(i -> i.subscription.topic.equals(topic) && i.onMessage != null && i.onMessage == onMessage);
    }

    public void syncApiRemove(String topic) {
        if (DEBUG && VERBOSE) {
            logger.info("(API) remove " + topic);
        }

        JsonObject ret = null;

        final MqttTopic topic1 = new MqttTopic(topic);

        if (clusterWide(topic1)) {
            retainedCluster.remove(topic1.topic);
        } else {
            retainedLocal.remove(topic1.topic);
        }
    }

    @Override
    public JsonObject syncApiPeek(String topic)
    {
        if (DEBUG && VERBOSE) {
            logger.info("(API) peek " + topic);
        }

        JsonObject ret = null;

        final MqttTopic topic1 = new MqttTopic(topic);
        PublishMessage publishMessage = null;

        if (clusterWide(topic1)) {
            publishMessage = retainedCluster.get(topic1.topic);
        } else {
            publishMessage = retainedLocal.get(topic1.topic);
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
    public void syncApiBroadcast(String topic, byte[] body, int qos, boolean retained)
    {
        if (DEBUG && VERBOSE) {
            logger.info("(API) broadcast " + topic);
        }

        ByteBuffer buffer = body != null ? ByteBuffer.wrap(body) : null;
        PublishMessage message = new PublishMessage();
        message.setTopicName(topic);
        message.setPayload(buffer);
        message.setQos(qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : (qos == 1 ? AbstractMessage.QOSType.MOST_ONE : AbstractMessage.QOSType.LEAST_ONE));
        message.setRetainFlag(retained);

        syncMessageForBroadcast(message);
    }

    public void syncMessageForBroadcast(PublishMessage publishMessage) {
        try {
            _messageForBroadcastQ.transfer(publishMessage);
        }
        catch (Throwable t) {
            logger.error("", t);
        }
    }

    private final Thread _retainedThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            final long now = new java.util.Date().getTime();
            retainedLocal.values().removeIf(publishMessage -> publishMessage.expires != 0l && publishMessage.expires < now);
            if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
        }
    });

    private final Thread _retainedClusterThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            final long now = new java.util.Date().getTime();
            ConcurrentLinkedQueue<String> toRemove = new ConcurrentLinkedQueue<>();
            retainedCluster.entrySet().stream().mapToLong(entry -> {
                final PublishMessage publishMessage = entry.getValue();
                if (publishMessage.expires != 0l && publishMessage.expires < now) {
                    toRemove.add(entry.getKey());
                    return 1l;
                }
                return 0l;
            }).sum();
            toRemove.stream().mapToLong(k -> {
                retainedCluster.remove(k);
                return 1l;
            }).sum();
            try { Thread.sleep(9997l); } catch (InterruptedException ie) { }
        }
    });

    private final LinkedTransferQueue<PublishMessage> _messageForBroadcastQ = new LinkedTransferQueue<PublishMessage>();
    private final Thread _messageForBroadcastThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            try {
                final ConcurrentLinkedQueue<PublishMessage> list = new ConcurrentLinkedQueue<>();
                _messageForBroadcastQ.drainTo(list);
                list.stream().mapToLong(publishMessage -> {
                    _messageForBroadcast(publishMessage);
                    return 1l;
                }).sum();
                if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
            }
            catch (Throwable t) {
                LoggerFactory.getLogger(SyncMqttBroker.class).fatal("", t);
            }
        }
    });

    public final void _messageForBroadcast(PublishMessage publishMessage) {
        final ContentHelper contentHelper = new ContentHelper(v -> publishMessage.getPayload(), v -> publishMessage.isRetainFlag(), v -> publishMessage.getTopicName());
        final MqttSubscription topicMatcher = new MqttSubscription(publishMessage.getTopicName(), (byte)0);

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
        internal.parallelStream().mapToLong(i -> {
            if (i.subscription.isWildcard()) return 0l;
            if (topicMatcher.matches(i.subscription)) {
                if (i.endPoint != null) {
                    vertx.eventBus().send(i.endPoint, contentHelper.json(), VERTXDEFINES.DELIVERY_OPTIONS);
                } else if (i.onMessage != null) {
                    try {
                        i.onMessage.accept(contentHelper.encapsulated());
                    }
                    catch (Throwable t) {
                        logger.error(i.subscription.toString(), t);
                    }
                } else {
                    throw new FabricError();
                }
                return 1l;
            }
            return 0l;
        }).sum();

        connected.parallelStream().mapToLong(myMqttServerProtocol -> {
            return myMqttServerProtocol.state.subscriptions.stream().mapToLong(subscription -> {
                if (subscription == null) return 0l;
                if (subscription.isWildcard()) return 0l;
                if (topicMatcher.matches(subscription)) {
                    myMqttServerProtocol.publish(subscription.topic, contentHelper.payload(), (byte)0, publishMessage.isRetainFlag());
                    return 1l;
                }
                return 0l;
            }).sum();
        }).sum();

        // note cannot retain broadcast messages
    }

    public class QosHelper
    {
        private AtomicBoolean match = new AtomicBoolean(false);
        private AtomicInteger qos = new AtomicInteger(0);
        public void qos(int qos) {
            this.qos.updateAndGet(q -> {
                q = qos > q ? qos : q;
                return q;
            });
            this.match.compareAndSet(false, true);
        }
        public boolean terminated() { return match.get() && qos.get() >= 2; }
        public boolean isMatch() { return match.get(); }
        public byte getQos() { return (byte) qos.get(); }
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
        retainedLocal.values().stream().mapToLong(publishMessage -> {

            final ContentHelper ch = new ContentHelper(v -> publishMessage.getPayload(), v -> publishMessage.isRetainFlag(), v -> publishMessage.getTopicName());
            final QosHelper qh = new QosHelper();

            newSubscriptions.stream().mapToLong(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    qh.qos(subscription.qos);
                    return 1l;
                }
                return 0l;
            }).anyMatch(l -> qh.terminated());

            if (qh.isMatch()) {
                myMqttServerProtocol.publish(publishMessage.getTopicName(), ch.payload(), qh.getQos(), publishMessage.isRetainFlag());
                return 1l;
            }
            return 0l;
        }).sum();
        retainedCluster.values().stream().mapToLong(publishMessage -> {

            final ContentHelper ch = new ContentHelper(v -> publishMessage.getPayload(), v -> publishMessage.isRetainFlag(), v -> publishMessage.getTopicName());
            final QosHelper qh = new QosHelper();

            newSubscriptions.stream().mapToLong(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    qh.qos(subscription.qos);
                    return 1l;
                }
                return 0l;
            }).anyMatch(l -> qh.terminated());

            if (qh.isMatch()) {
                myMqttServerProtocol.publish(publishMessage.getTopicName(), ch.payload(), qh.getQos(), publishMessage.isRetainFlag());
                return 1l;
            }
            return 0l;
        }).sum();
    }

    public final static class PM {
        public PM(MqttServerProtocol p, PublishMessage m) {
            this.p = p;
            this.m = m;
        }
        public final MqttServerProtocol p;
        public final PublishMessage m;
    }
    private final LinkedTransferQueue<PM> _messageArrivedQ = new LinkedTransferQueue<>();
    private final Thread _messageArrivedThread = new Thread(() -> {
        while (!CoreFabric.exit) {
            try {
                final ConcurrentLinkedQueue<PM> list = new ConcurrentLinkedQueue<>();
                _messageArrivedQ.drainTo(list);
                list.stream().mapToLong(pm -> {
                    _messageArrived(pm.p, pm.m);
                    return 1l;
                }).sum();
                if (SLOWER) { try { Thread.sleep(97l); } catch (InterruptedException ie) { } } else Thread.yield();
            }
            catch (Throwable t) {
                LoggerFactory.getLogger(SyncMqttBroker.class).fatal("", t);
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
        try {
            _messageArrivedQ.transfer(new PM(protocol, publishMessage));
        }
        catch (Throwable t) {
            logger.error("", t);
        }
    }

    public void _messageArrived(MqttServerProtocol protocol, PublishMessage publishMessage)
    {
        if (DEBUG && VERBOSE) {
            logger.info("Message arrived " + publishMessage.getTopicName());
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

        final ContentHelper contentHelper = new ContentHelper(v-> publishMessage.getPayload(), v -> publishMessage.isRetainFlag(), v -> publishMessage.getTopicName());

        // message arrived, send out to (all) subscribers
        internal.parallelStream().mapToLong(i -> {
            if (i.subscription.matches(publishMessage.getTopic())) {
                if (i.endPoint != null) {
                    vertx.eventBus().send(i.endPoint, contentHelper.json(), VERTXDEFINES.DELIVERY_OPTIONS);
                } else if (i.onMessage != null) {
                    try {
                        i.onMessage.accept(contentHelper.encapsulated());
                    }
                    catch (Throwable t) {
                        logger.error(i.subscription.toString(), t);
                    }
                } else {
                    throw new FabricError();
                }
                return 1l;
            }
            return 0l;
        }).sum();

        connected.parallelStream().mapToLong(myMqttServerProtocol -> {
            if (protocol != null) {
                if (protocol.noEcho && myMqttServerProtocol == protocol) return 0l; // used for bridges
            }

            if (myMqttServerProtocol == null) return 0l; // can't help you
            if (myMqttServerProtocol.state == null) return 0l;
            if (myMqttServerProtocol.state.subscriptions == null) return 0l;

            final QosHelper qh = new QosHelper();

            myMqttServerProtocol.state.subscriptions.stream().mapToLong(subscription -> {
                if (subscription.matches(publishMessage.getTopic())) {
                    qh.qos(subscription.qos);
                    return 1l;
                }
                return 0l;
            }).anyMatch(l -> qh.terminated());

            if (qh.isMatch()) {
                myMqttServerProtocol.publish(publishMessage.getTopicName(), contentHelper.payload(), qh.getQos(), publishMessage.isRetainFlag());
                return 1l;
            }
            return 0l;
        }).sum();

        // queue non-retained messages qos 1 or 2 only?
        if (!publishMessage.isRetainFlag() /* && publishMessage.getQos().ordinal() > 0 */) {
            disconnected.values().parallelStream().mapToLong(state -> {
                final QosHelper qh = new QosHelper();
                state.subscriptions.stream().mapToLong(subscription -> {
                    if (subscription.matches(publishMessage.getTopic())) {
                        qh.qos(subscription.qos);
                        return 1l;
                    }
                    return 0l;
                }).anyMatch(l -> qh.terminated());
                if (qh.isMatch()) {
                    state.enqueue(publishMessage);
                    return 1l;
                }
                return 0l;
            }).sum();
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
            protocol.logger.info("Subscribe message " + subscription.topic + " from " + myMqttServerProtocol.clientID);
        }

        myMqttServerProtocol.state.subscriptions.removeIf(s -> s.topic.equals(subscription.topic));
        myMqttServerProtocol.state.subscriptions.add(subscription);

        return subscription.qos;
    }

    public void unsubscribe(MqttServerProtocol protocol, String topic) {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;

        myMqttServerProtocol.state.subscriptions.removeIf(s -> s.topic.equals(topic));
    }

    public void disconnected(MqttServerProtocol protocol)
    {
        MyMqttServerProtocol myMqttServerProtocol = (MyMqttServerProtocol) protocol;
        if (DEBUG) {
            protocol.logger.info("Disconnected " + myMqttServerProtocol.clientID);
        }

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
    }
}
