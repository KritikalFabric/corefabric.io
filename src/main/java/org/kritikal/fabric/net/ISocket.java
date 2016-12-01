package org.kritikal.fabric.net;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Created by ben on 04/02/15.
 */
public interface ISocket {

    public void closeHandler(Handler<Void> handler);

    public void close();

    public void write(Buffer buffer);

    public void dataHandler(Handler<Buffer> handler);

}
