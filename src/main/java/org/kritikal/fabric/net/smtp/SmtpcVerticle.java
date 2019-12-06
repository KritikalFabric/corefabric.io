package org.kritikal.fabric.net.smtp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.net.NetSocket;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.VERTXDEFINES;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by ben on 5/15/14.
 */
public abstract class SmtpcVerticle extends AbstractVerticle implements Handler<Message<JsonObject>>
{
    private static volatile int numberOfOpenSockets = 0;
    private final int OPENSOCKETS_HIGHWATERMARK = 4000;
    private final String address;
    public SmtpcVerticle(final String address)
    {
        this.address = address;
    }

    // address (@example.com) -> Set of active delivery agents
    private volatile static ConcurrentHashMap<String, ConcurrentLinkedQueue<NetSocket>> pool
            = new ConcurrentHashMap<String, ConcurrentLinkedQueue<NetSocket>>();

    private volatile static ConcurrentHashMap<String, Date> blacklistedIpAddresses
            = new ConcurrentHashMap<String, Date>();

    private volatile static ConcurrentHashMap<String, Date> blacklistedDomains
            = new ConcurrentHashMap<String, Date>();

    public Logger logger = null;
    private MessageConsumer<JsonObject> mc = null;

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        mc = vertx.eventBus().localConsumer(address, this);
        vertx.eventBus().send(address + ".ready", new JsonObject().put("ok", true), VERTXDEFINES.DELIVERY_OPTIONS);
    }

    public void stop() throws Exception
    {
        if (mc != null) mc.unregister();
        vertx.eventBus().send(address + ".ready", new JsonObject().put("ok", false), VERTXDEFINES.DELIVERY_OPTIONS);
        emptyPool();
        super.stop();
    }

    private final static String pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
    private final static SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
    public final static String formatSmtpDateHeader(java.util.Date date) {
        return format.format(date);
    }

    public void emptyPool()
    {
        ConcurrentHashMap<String, ConcurrentLinkedQueue<NetSocket>> oldPool = pool;
        pool = new ConcurrentHashMap<String, ConcurrentLinkedQueue<NetSocket>>();
        oldPool.forEach(new BiConsumer<String, ConcurrentLinkedQueue<NetSocket>>() {
            @Override
            public void accept(String s, ConcurrentLinkedQueue<NetSocket> netSockets) {
                netSockets.forEach(new Consumer<NetSocket>() {
                    @Override
                    public void accept(NetSocket netSocket) {
                        try { netSocket.close(); } catch (Throwable t) { }
                    }
                });
            }
        });
    }

    public void flushPool()
    {
        pool.forEach(new BiConsumer<String, ConcurrentLinkedQueue<NetSocket>>() {
            @Override
            public void accept(String s, ConcurrentLinkedQueue<NetSocket> netSockets) {
                netSockets.forEach(new Consumer<NetSocket>() {
                    @Override
                    public void accept(NetSocket netSocket) {
                        try { netSocket.close(); } catch (Throwable t) { }
                    }
                });
            }
        });
    }


    public abstract SmtpcProtocolController newController(final Message<JsonObject> message, final SmtpTransactionState state);

    public void handle(final Message<JsonObject> message) {
        final SmtpTransactionState state = new SmtpTransactionState(message.body());
        final SmtpcProtocolController controller = newController(message, state);
        if (state.message.to.size() != 1) {
            controller.handleProtocolFailure(null);
            return;
        }

        logger.debug(address + " mail from <" + state.message.from + "> to <" + state.message.to.get(0) + "> retry " + state.retryCount);

        String _domain = state.message.to.get(0);
        int i = _domain.indexOf('@');
        if (i >= 0) {
            _domain = _domain.substring(i + 1);
        }
        else {
            controller.handleProtocolFailure(Buffer.buffer("500 no delivery domain", Constants.ASCII));
            return;
        }

        final String domain = _domain;
        if (!state.deliveryInformation.skipBlacklist) {
            try {
                Date blacklistedDomainUntil = blacklistedDomains.get(domain);
                if (blacklistedDomainUntil.getTime() > new Date().getTime()) {
                    controller.handleProtocolFailure(Buffer.buffer("400 Blacklisted domain", Constants.ASCII));
                    return;
                }
            } catch (Exception e) {
                // good, not blacklisted.
            }
        }

        // first resolve all MX records and associated DNS entries
        // when each stage of name resolution is done post message back to self
        if (state.deliveryInformation.useMx) {
            if (!state.deliveryInformation.readyForConnect) {
                if (!state.deliveryInformation.haveLookedUpMxList) {
                    state.deliveryInformation.haveLookedUpMxList = true;
                    DnsClient client = vertx.createDnsClient(53, CoreFabric.ServerConfiguration.resolver);
                    state.tainted = true;
                    client.resolveMX(domain, new Handler<AsyncResult<List<MxRecord>>>() {
                        @Override
                        public void handle(AsyncResult<List<MxRecord>> result) {
                            if (result.succeeded()) {
                                SmtpTransactionState state1 = new SmtpTransactionState(state.toJsonObject());
                                List<MxRecord> list = result.result();
                                if (list.size() == 0) {
                                    controller.handleProtocolFailure(Buffer.buffer("500 no MX records", Constants.ASCII));
                                    blacklistedDomains.put(domain, new Date(new Date().getTime() + 5 * 60 * 1000)); // blacklist for 5 mintes
                                } else {
                                    list.sort(new Comparator<MxRecord>() {
                                        @Override
                                        public int compare(MxRecord o1, MxRecord o2) {
                                            return Integer.compare(o1.priority(), o2.priority());
                                        }
                                    });
                                    for (int i = 0; i < list.size(); ++i) {
                                        state1.deliveryInformation.remainingMxListForDns.add(list.get(i).name());
                                    }

                                    vertx.eventBus().send(address, state1.toJsonObject(), VERTXDEFINES.DELIVERY_OPTIONS); // next step
                                }
                            } else {
                                controller.handleProtocolFailure(Buffer.buffer("500 could not resolve MX", Constants.ASCII));
                                blacklistedDomains.put(domain, new Date(new Date().getTime() + 5 * 60 * 1000)); // blacklist for 5 mintes
                            }
                        }
                    });
                }

                if (state.tainted) return;

                String mxName = state.deliveryInformation.mxName();
                if (mxName != null) {
                    DnsClient client = vertx.createDnsClient(53, CoreFabric.ServerConfiguration.resolver);
                    state.tainted = true;
                    client.resolveA(mxName, new Handler<AsyncResult<List<String>>>() {
                        @Override
                        public void handle(AsyncResult<List<String>> event) {
                            SmtpTransactionState state1 = new SmtpTransactionState(state.toJsonObject());
                            if (event.succeeded()) {
                                List<String> list = event.result();
                                for (int i = 0; i < list.size(); ++i) {
                                    state1.deliveryInformation.remainingIpAddressesForConnect.add(list.get(i));
                                }
                            } else {
                                // ignore error at this point
                            }

                            state1.tainted = true;
                            client.resolveAAAA(mxName, new Handler<AsyncResult<List<String>>>() {
                                @Override
                                public void handle(AsyncResult<List<String>> event) {
                                    SmtpTransactionState state2 = new SmtpTransactionState(state1.toJsonObject());
                                    if (event.succeeded()) {
                                        List<String> list = event.result();
                                        for (int i = 0; i < list.size(); ++i) {
                                            state2.deliveryInformation.remainingIpAddressesForConnect.add(list.get(i));
                                        }
                                    } else {
                                        // ignore error at this point
                                    }

                                    vertx.eventBus().send(address, state2.toJsonObject(), VERTXDEFINES.DELIVERY_OPTIONS);
                                }
                            });
                        }
                    });
                    return;
                }

                if (state.tainted) return;

                state.deliveryInformation.readyForConnect = true;
            }
        }

        if (state.tainted) return;

        final String connectAddress = state.deliveryInformation.useMx ? state.deliveryInformation.connectAddress() : state.deliveryInformation.smartHost;
        if (connectAddress == null) {
            controller.handleProtocolFailure(Buffer.buffer("400 no IP addresses remaining to try", Constants.ASCII));
            return;
        }

        if (!state.deliveryInformation.skipBlacklist) {
            try {
                Date blacklistedIpUntil = blacklistedIpAddresses.get(connectAddress);
                if (blacklistedIpUntil.getTime() > new Date().getTime()) {
                    controller.handleProtocolFailure(Buffer.buffer("400 Blacklisted IP address", Constants.ASCII));
                    return;
                }
            } catch (Exception e) {
                // good, not blacklisted.
            }
        }
        final String cacheKey = connectAddress + " " + state.deliveryInformation.smartPort;

        pool.putIfAbsent(cacheKey, new ConcurrentLinkedQueue<NetSocket>());
        NetSocket s = null;
        try {
            s = pool.get(cacheKey).remove();
        } catch (Exception ex) {
            s = null;
        }

        // Re-use from pool?
        if (s != null) {
            final NetSocket socket = s;
            state.state = SmtpTransactionStateEnum.HELO;
            wireUpProtocol(socket, state, cacheKey, controller, connectAddress, "");  // TODO: keep mx name
            socket.write("RSET\r\n");
        } else { // Create new and use that instead

            if (numberOfOpenSockets >= OPENSOCKETS_HIGHWATERMARK)
            {
                flushPool(); // *and* carry on.
            }

            logger.warn("Creating new connection to " + connectAddress + " port " + state.deliveryInformation.smartPort);
            vertx.createNetClient()
                    // vertx2 .setConnectTimeout(30 * 1000) // 30 seconds
                    // vertx2 .setSoLinger(0)
                    // vertx2 .setTCPNoDelay(true)
                    // vertx2 .setTCPKeepAlive(true)
                    // vertx2 .setUsePooledBuffers(true)
                    .connect(state.deliveryInformation.smartPort, connectAddress, new Handler<AsyncResult<NetSocket>>() {
                        @Override
                        public void handle(AsyncResult<NetSocket> event) {
                            if (event.succeeded()) {
                                final NetSocket socket = event.result();
                                ++numberOfOpenSockets;
                                wireUpProtocol(socket, state, cacheKey, controller, connectAddress, ""); // TODO: keep mx name
                            } else {
                                blacklistedIpAddresses.put(connectAddress, new Date(new Date().getTime() + 5 * 60 * 1000));
                                if (state.deliveryInformation.useMx)
                                    vertx.eventBus().send(address, state.toJsonObject(), VERTXDEFINES.DELIVERY_OPTIONS); // try again
                                else {
                                    controller.handleProtocolFailure(Buffer.buffer("400 Unable to connect", Constants.ASCII));
                                }
                            }
                        }
                    });
        }
    }

    // Same for pooled, non-pooled thus put here.
    final void wireUpProtocol(final NetSocket socket, final SmtpTransactionState state, final String cacheKey,
                                       final SmtpcProtocolController controller, final String connectAddress, final String connectName)
    {
        final SmtpcProtocol protocol = new SmtpcProtocol(socket, state, connectAddress, connectName) {
            @Override
            public void handleSentOk() {
                controller.handleSubmission();

                pool.putIfAbsent(cacheKey, new ConcurrentLinkedQueue<NetSocket>());
                socket.handler(null).closeHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        --numberOfOpenSockets;
                        ConcurrentLinkedQueue<NetSocket> oldQ = pool.replace(cacheKey,
                                new ConcurrentLinkedQueue<NetSocket>());
                        oldQ.forEach(new Consumer<NetSocket>() {
                            @Override
                            public void accept(NetSocket netSocket) {
                                if (netSocket != socket) pool.get(cacheKey).add(netSocket);
                            }
                        });
                    }
                });
                pool.get(cacheKey).add(socket);
            }

            @Override
            public void handleFailure(Buffer lastError) {
                state.message.failure = true;
                controller.handleProtocolFailure(lastError);
            }

            @Override
            public void handleClose() {
                if (!state.message.failure && !state.message.submitted) {
                    controller.handleProtocolFailure(null);
                }
            }
        };

        socket.handler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer event) {
                protocol.appendBuffer(event);
            }
        });
        socket.closeHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                --numberOfOpenSockets;
                protocol.handleClose();
            }
        });
    }
}
