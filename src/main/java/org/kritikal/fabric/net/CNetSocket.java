package org.kritikal.fabric.net;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

/**
 * Created by ben on 04/02/15.
 */
public class CNetSocket implements ISocket {

    public CNetSocket(final NetSocket netSocket) {
        this.netSocket = netSocket;
        this.netSocket.setWriteQueueMaxSize(16*1024*1024); // 16MB
    }

    final NetSocket netSocket;

    @Override
    public void closeHandler(Handler<Void> handler) {
        netSocket.closeHandler(handler);
    }

    @Override
    public void close() {
        try {
            netSocket.close();
        }
        catch (Throwable t) { }
    }

    @Override
    public void write(Buffer buffer) {
        if (netSocket.writeQueueFull()) {
            netSocket.drainHandler(new Handler<Void>() {
                @Override
                public void handle(Void v) {
                    netSocket.resume();
                }
            });
            netSocket.pause(); // pauses input, drain handler above will be called when ready
        }
        netSocket.write(buffer);
    }

    @Override
    public void dataHandler(Handler<Buffer> handler) {
        netSocket.handler(handler);
    }
}
