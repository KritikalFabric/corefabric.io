package org.kritikal.fabric.net;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrame;
import org.kritikal.fabric.core.exceptions.FabricError;

/**
 * Created by ben on 04/02/15.
 */
public class CServerWebSocket implements ISocket {

    public CServerWebSocket(final ServerWebSocket webSocket, final String corefabric)
    {
        if (corefabric==null) throw new FabricError("corefabric");
        this.webSocket = webSocket; this.corefabric = corefabric;
    }

    final ServerWebSocket webSocket;
    public final String corefabric;

    @Override
    public void closeHandler(Handler<Void> handler) {
        webSocket.closeHandler(handler);
    }

    @Override
    public void close() {
        try {
            webSocket.close();
        }
        catch (Throwable t) { }
    }

    @Override
    public void write(Buffer buffer) {
        if (webSocket.writeQueueFull()) {
            webSocket.drainHandler(new Handler<Void>() {
                @Override
                public void handle(Void v) {
                    webSocket.resume();
                }
            });
            webSocket.pause(); // pauses input, drain handler above will be called when ready
        }
        webSocket.write(buffer);
    }

    @Override
    public void dataHandler(Handler<Buffer> handler) {
        webSocket.handler(handler);
    }
}
