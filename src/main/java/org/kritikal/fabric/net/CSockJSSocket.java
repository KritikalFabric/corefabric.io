package org.kritikal.fabric.net;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.handler.sockjs.SockJSSocket;

import java.util.Base64;

/**
 * Created by ben on 11/02/15.
 */
public class CSockJSSocket implements ISocket {

    public CSockJSSocket(final SockJSSocket sockJSSocket) { this.sockJSSocket = sockJSSocket; }

    final SockJSSocket sockJSSocket;
    Handler<Void> closeHandler;

    @Override
    public void closeHandler(Handler<Void> handler) { this.closeHandler = handler; }

    @Override
    public void close() {
        try {
            sockJSSocket.close();
        }
        catch (Throwable t) { }
        try {
            closeHandler.handle(null);
        }
        catch (Throwable t) { }
    }

    @Override
    public void write(Buffer buffer) {
        if (sockJSSocket.writeQueueFull()) {
            sockJSSocket.drainHandler(new Handler<Void>() {
                @Override
                public void handle(Void v) {
                    sockJSSocket.resume();
                }
            });
            sockJSSocket.pause(); // pauses input, drain handler above will be called when ready
        }

        byte[] base64 = Base64.getEncoder().encode(buffer.getBytes());
        sockJSSocket.write(Buffer.buffer(base64));
    }

    @Override
    public void dataHandler(Handler<Buffer> handler) {
        sockJSSocket.handler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer event) {
                byte[] base64 = event.getBytes();
                byte[] binary = Base64.getDecoder().decode(base64);
                handler.handle(Buffer.buffer(binary));
            }
        });
    }

}
