package io.corefabric.pi.appweb.providers;

import com.cisco.qte.jdtn.apps.AbstractApp;
import com.cisco.qte.jdtn.bp.*;
import com.cisco.qte.jdtn.general.*;
import io.netty.util.internal.ConcurrentSet;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.VertxHelpers;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.exceptions.FabricError;
import io.corefabric.pi.appweb.DocApiCall;
import io.corefabric.pi.appweb.IDocApiProvider;
import io.corefabric.pi.appweb.UIDocApiWorkerVerticle;
import io.corefabric.pi.appweb.json.DtnConfigJson;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Created by ben on 11/5/16.
 */
public class DtnConfigProvider implements IDocApiProvider {
    private final static Logger logger = LoggerFactory.getLogger(DtnConfigProvider.class);
    public final static String PATH = "/dtn-config";

    private DocApiCall __init(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = new DocApiCall(context, context.cfg.instancekey + PATH);
        __prepopulate(call);
        return call;
    }

    private DocApiCall __reinit(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = new DocApiCall(context, context.cfg.instancekey + PATH, context.apicall.getJsonObject("ad").getJsonObject("data"));
        __prepopulate(call);
        return call;
    }

    private void __prepopulate(DocApiCall call) {
        new DtnConfigJson().apply().iterator().forEachRemaining(new Consumer<Map.Entry<String, Object>>() {
            @Override
            public void accept(Map.Entry<String, Object> me) {
                call.o.put(me.getKey(), me.getValue());
            }
        });
    }

    static AppRegistration appRegistration = null;

    public static class CorefabricPingApp extends AbstractApp {

        public static final String APP_NAME = "CorefabricPing";
        public static final String APP_PATTERN =
                "corefabric" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
        public static final int QUEUE_DEPTH = 10;

        public CorefabricPingApp(String[] args) throws BPException {
            super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
        }

        private final static ConcurrentHashSet<String> inflight = new ConcurrentHashSet<>();

        @Override
        public void startupImpl() {
            DtnConfigProvider.appRegistration = getAppRegistration();
        }
        @Override
        public void shutdownImpl() {
        }
        private static final java.util.logging.Logger _logger =
                java.util.logging.Logger.getLogger(CorefabricPingApp.class.getCanonicalName());

        @Override
        public void threadImpl() throws JDtnException, InterruptedException {
            Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
            EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
            EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
            Payload payload = bundle.getPayloadBundleBlock().getPayload();
            byte[] payloadBytes = payload.getBodyDataBuffer();
            String message = null;
            try {
                message = new String(payloadBytes, "UTF-8");
            }
            catch (UnsupportedEncodingException lol) { /* ignore me */ }

            logger.warn("Recv PING from " + source.toString() + " to " + dest.toString() + " " + message);

            // Rules for our "invented" ping functionality Mark I:
            // If the destination Eid exactly matches our node Eid (e.g., dtn:node,
            // without any appended app identifiers), then this is
            // a ping request and we need to reply to it.  We reply to it by setting:
            // SourceEid = our node Eid + "/Admin"
            // DestEid = Received Bundle Source Eid
            if (dest.equals(BPManagement.getInstance().getEndPointIdStem().append("/corefabric"))) {
                if (GeneralManagement.isDebugLogging()) {
                    _logger.fine("Sending Reply");
                }
                try {
                    dest = EndPointId.createEndPointId(source.append("/reply"));
                    source = EndPointId.createEndPointId(BPManagement.getInstance().getEndPointIdStem().append("/corefabric/request/handler"));
                    Payload payload1 = new Payload(payloadBytes, 0, payloadBytes.length);
                    BpApi.getInstance().sendBundle(
                            getAppRegistration(),
                            source,
                            dest,
                            payload1,
                            null);
                    logger.warn("Send PONG from " + source.toString() + " to " + dest.toString() + " " + message);
                } catch (Throwable e) {
                    _logger.severe("Error sending Ping reply: " + e.getMessage());
                    _logger.severe("Error ignored");
                }
            }

            // Otherwise, it could be a ping reply. A Ping Reply is distinguished
            // by a destination EID of the form "dtn:node/corefabric/reply"
            // We need to deliver it to the
            // foreground ping origination process.
            else if (dest.getEndPointIdString().endsWith("/corefabric/request/reply")) {
                if (GeneralManagement.isDebugLogging()) {
                    _logger.fine("Delivering to PingReplyQueue");
                }
                inflight.add(message);
            }

            // Otherwise it is a mis-addressed Bundle; no app registrations matched
            // it and we don't match it.
            else {
                _logger.severe("No Application is registered to handle incoming Bundle; " +
                        "Destination EID= " + dest.getEndPointIdString());
                _logger.severe("Source EID: " + source.getEndPointIdString());
            }
            payload.delete();
        }
    }

    private void __complete(DocApiCall call) {
        // TODO: ping each server via dtn to show if they are up/down/unknown...

        final JsonObject dtn = call.o.getJsonObject("dtn");
        final JsonArray dtnMqttBridge = call.o.getJsonArray("dtn-mqtt-bridge");
        final ConcurrentSet<JsonObject> objects =new ConcurrentSet<>();

        VertxHelpers.compute(call.o, dtn, (object)->{
            if (!dtn.getBoolean("isRouter")) {
                __updatePing(call, dtn, "checking");
            }
            for (int i = 0, l = dtnMqttBridge.size(); i < l; ++i) {
                dtnMqttBridge.getJsonObject(i).put("noAppPing", true);
                __updatePing(call, dtnMqttBridge.getJsonObject(i), "checking");
            }
            return object;
        });

        if (call.hasExited()) return;
        call.publish();
        final JsonObject stats = new JsonObject();
        call.o.put("stats", stats);
        __updateStats(call, stats);

        if (!dtn.getBoolean("isRouter")) {
            __checkDtnPing(call, dtn, dtn, "dtn://" + dtn.getString("routerHostname") + "/corefabric", objects);
        }
        for (int i = 0, l = dtnMqttBridge.size(); i < l; ++i) {
            final JsonObject o = dtnMqttBridge.getJsonObject(i);
            __checkDtnPing(call, dtn, o, o.getString("to").replace("/MqttBridge", "/corefabric"), objects);
        }
        __updateConfig(call, dtn, objects);

        //
        if (call.hasExited()) return;
        call.publish();
    }

    private static void __updatePing(DocApiCall call, JsonObject statusNode, String result) {
        VertxHelpers.compute(call.o, statusNode, (status) -> {
            if ("ko".equals(result)&&status.containsKey("noAppPing")&&status.getBoolean("noAppPing")) return status;
            final String original = status.getString("ping");
            if (original != null && "checking".equals(result)) return status;
            if ("up".equals(original)) return status;
            if ("ok".equals(original)&&!("up".equals(result))) return status;
            if ("ko".equals(original)&&!("up".equals(result)||"ok".equals(result))) return status;
            if ("partial".equals(original)&&!("up".equals(result)||"ok".equals(result)||"ko".equals(result))) return status;
            if ("down".equals(original)&&!("up".equals(result)||"ok".equals(result)||"ko".equals(result)||"partial".equals(result))) return status;
            status.put("ping", result);
            return status;
        });
    }
    private String getHostname(String foo) {
        int idx = foo.indexOf("//");
        if (idx >= 0) foo = foo.substring(idx + 2);
        idx = foo.indexOf("/");
        if (idx >= 0) foo = foo.substring(0, idx);
        return foo;
    }
    private void __checkDtnPing(DocApiCall call, JsonObject dtn, JsonObject statusNode, String toEid, final ConcurrentSet<JsonObject> objects) {
        final int QUEUE_DEPTH = 10;
        final long PING_LIFETIME_SECS = 4;
        final long PING_REPLY_WAIT_MSECS = 10000L;
        final int count = 1;

        VertxHelpers.compute(call.o, statusNode, (status) -> {
            status.put("hostname", getHostname(toEid));
            objects.add(status);
            return status;
        });

        try {
            EndPointId destEid = EndPointId.createEndPointId(toEid);
            EndPointId sourceEid = EndPointId.createEndPointId(
                    BPManagement.getInstance().getEndPointIdStem().append("/corefabric/request"));

            //send loop starts
            long minTime = Long.MAX_VALUE;
            long maxTime = Long.MIN_VALUE;
            long averageTime = 0;
            int receiveCount = 0;
            int errorCount = 0;
            int _transmitCount = 0;
            for (; _transmitCount < count; _transmitCount++) {

                final int transmitCount = _transmitCount;

                CoreFabric.getVertx().executeBlocking(f -> {

                    try {
                        //callbacks.onPingRequest(destEid, transmitCount, count);
                        final  String message = UUID.randomUUID().toString();
                        byte[] payloadBytes = message.getBytes("UTF-8");

                        // Send Ping Request
                        Payload payload = new Payload(payloadBytes, 0, payloadBytes.length);
                        BundleOptions bundleOptions = new BundleOptions();
                        bundleOptions.lifetime = PING_LIFETIME_SECS;
                        BpApi.getInstance().sendBundle(
                                DtnConfigProvider.appRegistration,
                                sourceEid,
                                destEid,
                                payload,
                                null);
                        logger.warn("Sent PING request from " + sourceEid.toString() + " to " + destEid.toString() + " " + message);

                        long abortAfter = new java.util.Date().getTime() + PING_REPLY_WAIT_MSECS;
                        boolean found = false;
                        while (!found) {
                            if (found = CorefabricPingApp.inflight.contains(message)) {
                                CorefabricPingApp.inflight.remove(message);
                            } else {
                                if (new java.util.Date().getTime() > abortAfter) break;
                                Thread.yield();
                            }
                        }
                        if (found) {
                            f.complete("up");
                            return;
                        } else {
                            f.complete("down");
                            return;
                        }

                    } catch (Throwable e) {
                        logger.warn(PATH, e);
                        f.complete("error");
                        /*_logger.log(Level.SEVERE, "ping", e);
                        callbacks.onPingException(e);*/
                    }

                }, r -> {
                    if (r.failed()) {
                        __updatePing(call, statusNode, "error");
                    } else {
                        __updatePing(call, statusNode, r.result().toString());
                    }
                    if (call.hasExited()) return;
                    call.publish();
                });


            }
            /*if (receiveCount > 0) {
                callbacks.onPingDone(transmitCount, receiveCount, minTime, maxTime, averageTime);
            } else {
                // no reply's received
                callbacks.onNoPingReplies(destEid);
            }*/

        } catch (Exception e) {
            logger.warn(PATH, e);

            //_logger.log(Level.SEVERE, "ping", e);
            //callbacks.onPingException(e);
        }
    }
    private void __updateConfig(DocApiCall call, JsonObject dtn, final ConcurrentSet<JsonObject> objects) {
        {
            JsonArray ary = dtn.getJsonArray("links");
            for (Link link : LinksList.getInstance().getLinks()) {
                final JsonObject o1 = new JsonObject();
                o1.put("name", link.getName());
                o1.put("type", Link.getLinkTypeString(link.getLinkType()));
                o1.put("operational", link.isLinkOperational());
                ary.add(o1);
            }
        }
        {
            final JsonArray ary = dtn.getJsonArray("neighbors");

            NeighborsList.getInstance().iterator().forEachRemaining(neighbor -> {
                final JsonObject o = new JsonObject();
                String name = neighbor.getName();
                o.put("name", name);
                o.put("hostname", name);
                boolean operational = neighbor.isNeighborOperational();
                o.put("ping", operational ? "partial" : "checking");
                VertxHelpers.compute(call.o, o, (object) -> {
                    ary.add(object);
                    return object;
                });
                final int n = ary.size()-1;

                if (operational) {
                    objects.forEach(object1 -> {
                        VertxHelpers.compute(call.o, object1, (object) -> {
                            if (name.equals(object.getString("hostname", ""))) {
                                __updatePing(call, object, "partial");
                            }
                           return object;
                        });
                    });

                    neighbor.getLinkAddresses().forEach(linkAddress -> {
                        try {
                            final Buffer buffer = Buffer.buffer();

                            HttpClientOptions clientOptions = new HttpClientOptions();
                            clientOptions.setConnectTimeout(5000);
                            final HttpClient httpClient = CoreFabric.getVertx().createHttpClient(clientOptions);
                            CoreFabric.getVertx().executeBlocking(f -> {
                                final String ip = linkAddress.getAddress().toParseableString();
                                if (ip.startsWith("127.")) {
                                    f.complete();
                                    return;
                                }
                                logger.info("requesting status from " + name + " on " + ip);
                            /*final HttpClientRequest req = */httpClient.getNow(1080, ip, "/api/json/status", response -> {
                                    response.handler(buffer::appendBuffer).endHandler(t -> {
                                        VertxHelpers.compute(call.o, dtn, (x) -> {
                                            String newStatus = "ko";

                                            try {
                                                final String data = new String(buffer.getBytes(), "UTF-8");
                                                logger.info(data);
                                                JsonObject jsonObject = new JsonObject(data);
                                                newStatus = jsonObject != null && jsonObject.containsKey("up") && jsonObject.getBoolean("up") ? "ok" : "ko";
                                            } catch (Exception e) {
                                                logger.warn("json", e);
                                                newStatus = "ko";
                                            }

                                            final String ns = newStatus;
                                            __updatePing(call, x.getJsonArray("neighbors").getJsonObject(n), ns); // !!! NOT __updatePing(o ,...)!!!
                                            objects.forEach(object -> {
                                                if (name.equals(object.getString("hostname", ""))) {
                                                    __updatePing(call, object, ns);
                                                }
                                            });
                                            logger.info("updated " + name + " to " + newStatus);
                                            f.complete();

                                            return dtn;
                                        });
                                    }).exceptionHandler(t -> {
                                        logger.warn("getting status", t);
                                        f.complete();
                                    });
                                });
                            }, false, r->{
                                if (call.hasExited()) return;
                                call.publish();
                                httpClient.close();
                            });
                        } catch (Throwable t) {
                            if (CoreFabric.ServerConfiguration.DEBUG)
                                logger.info("Pinging status for neighbour " + name, t);
                        }
                    });
                }

                VertxHelpers.compute(call.o, o, (object) -> {
                    object.put("type", neighbor.getType().toString());
                    object.put("operational", operational);
                    object.put("scheduledUp", neighbor.isNeighborScheduledUp());
                    return object;
                });
            });
        }
    }
    private void __updateStats(DocApiCall call, JsonObject statsNode) {
        CoreFabric.getVertx().executeBlocking(f -> {
            long end = new java.util.Date().getTime() + 120000;
            while(!call.hasExited()&&new java.util.Date().getTime()<end) {
                BpStats bpStats = BPManagement.getInstance().getBpStats();
                VertxHelpers.compute(call.o, statsNode, stats-> {
                    stats.put("nDataBundlesSourced",bpStats.nDataBundlesSourced);
                    stats.put("nDataBundlesReceived",bpStats.nDataBundlesReceived);
                    stats.put("nDataBundlesFwded",bpStats.nDataBundlesFwded);
                    stats.put("nDataBundlesDelivered",bpStats.nDataBundlesDelivered);
                    stats.put("nStatusReportsSent",bpStats.nStatusReportsSent);
                    stats.put("nStatusReportsReceived",bpStats.nStatusReportsReceived);
                    stats.put("nCustodySignalsSent",bpStats.nCustodySignalsSent);
                    stats.put("nCustodySignalsReceived",bpStats.nCustodySignalsReceived);
                    stats.put("nBundlesExpired",bpStats.nBundlesExpired);
                    stats.put("nCustodySignalsExpired",bpStats.nCustodySignalsExpired);
                    stats.put("nRetainedBytes",bpStats.nRetainedBytes);
                    stats.put("nEncodingMSecs",bpStats.nEncodingMSecs);
                    stats.put("nDecodingMSecs",bpStats.nDecodingMSecs);
                    return stats;
                });
                call.publish();

                try { Thread.sleep(1000); } catch (InterruptedException ie) { }
            }
        }, r -> {
            // do nothing
        });
    }

    @Override
    public void open_singleton(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = __init(context);

        call.publish();
        call.reply();

        __complete(call);
    }

    @Override
    public void open(UIDocApiWorkerVerticle.Context context) {
        throw new FabricError();
    }

    @Override
    public void list(UIDocApiWorkerVerticle.Context context) {
        throw new FabricError();
    }

    @Override
    public void upsert(UIDocApiWorkerVerticle.Context context) { throw new FabricError(); }

    public void disconnect(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = __reinit(context);

        call.publish();
        call.reply();

        // do nothing more
    }
}

