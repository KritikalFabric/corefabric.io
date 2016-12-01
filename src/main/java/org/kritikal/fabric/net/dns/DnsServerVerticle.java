package org.kritikal.fabric.net.dns;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.AbstractVerticle;

/**
 * Created by ben on 16/03/15.
 */
public abstract class DnsServerVerticle extends AbstractVerticle {

    public Logger logger = null;
    DatagramSocket udpListener = null;
    NetServer tcpListener = null;

    @Override
    public void start() throws Exception {

        super.start();

        logger = LoggerFactory.getLogger(getClass());

        final Handler<AsyncResult<DatagramSocket>> datagramHandler = new Handler<AsyncResult<DatagramSocket>>() {
            @Override
            public void handle(AsyncResult<DatagramSocket> event) {
                // handle incoming udp queries
                if (event.succeeded()) {
                    final DatagramSocket socket = event.result();
                    socket.handler(new Handler<DatagramPacket>() {
                        @Override
                        public void handle(DatagramPacket event) {
                            vertx.executeBlocking(f -> {
                                DnsQueryMessage query = new DnsQueryMessage(event.data().getBytes());
                                DnsResponseMessage response = new DnsResponseMessage(query);
                                if (query.questions != null) {
                                    for (DnsQuestionEntry question : query.questions)
                                        respondTo(response, question);
                                }
                                f.complete(response);
                            }, false, r -> {
                                if (!r.succeeded()) return;
                                DnsResponseMessage dnsResponseMessage = (DnsResponseMessage) r.result();
                                try {
                                    socket.send(dnsResponseMessage.createBuffer(true), event.sender().port(), event.sender().host(), null);
                                }
                                catch (Throwable t) {
                                    logger.warn("Error handling UDP data", t);
                                    socket.close();
                                }
                            });
                        }
                    });
                } else {
                    // do nothing in this case.
                }
            }
        };

        final Handler<NetSocket> tcpConnectHandler = new Handler<NetSocket>() {
            @Override
            public void handle(NetSocket socket) {
                // handle incoming tcp connection
                socket.handler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer event) {
                        vertx.executeBlocking(f -> {
                            DnsQueryMessage query = new DnsQueryMessage(event.getBytes());
                            DnsResponseMessage response = new DnsResponseMessage(query);
                            if (query.questions != null) {
                                for (DnsQuestionEntry question : query.questions)
                                    respondTo(response, question);
                            }
                            f.complete(response);
                        }, false, r -> {
                            if (!r.succeeded()) return;
                            DnsResponseMessage dnsResponseMessage = (DnsResponseMessage) r.result();
                            try {
                                socket.write(dnsResponseMessage.createBuffer(false));
                            }
                            catch (Throwable t) {
                                logger.warn("Error handling TCP data", t);
                                socket.close();
                            }
                        });
                    }
                });
            }
        };

        udpListener =
                vertx.createDatagramSocket()
                        .listen(CoreFabric.ServerConfiguration.PRODUCTION ? 53 : 1053, "0.0.0.0", datagramHandler);

        tcpListener =
                vertx.createNetServer()
                        .connectHandler(tcpConnectHandler)
                        .listen(CoreFabric.ServerConfiguration.PRODUCTION ? 53 : 1053);

    }

    @Override
    public void stop() throws Exception {
        tcpListener.close();;

        udpListener.close();

        super.stop();
    }

    /**
     * This is where you implement your DNS server :-)
     */
    public abstract void respondTo(DnsResponseMessage response, DnsQuestionEntry questionEntry);
}
