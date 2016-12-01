package org.kritikal.fabric.net.http;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ben on 15/11/15.
 */
public class BinaryBodyHandler implements BodyHandler {

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        BHandler handler = new BHandler(context);
    }

    @Override
    public BodyHandler setBodyLimit(long bodyLimit) {
        return this;
    }

    @Override
    public BodyHandler setUploadsDirectory(String uploadsDirectory) {
        return this;
    }

    @Override
    public BodyHandler setDeleteUploadedFilesOnEnd(boolean value) { return this; }

    @Override
    public BodyHandler setMergeFormAttributes(boolean mergeFormAttributes) {
        return this;
    }

    private class BHandler implements Handler<Buffer> {

        RoutingContext context;
        Buffer body = Buffer.buffer();
        boolean failed;
        boolean ended;
        long bodyLimit = 1024*1024;

        public BHandler(RoutingContext context) {
            this.context = context;
            context.request().exceptionHandler(context::fail);
            context.request().endHandler(v -> { doEnd(); });
            context.request().handler(this);
            context.setBody(body);
        }

        @Override
        public void handle(Buffer buff) {
            if (failed) {
                return;
            }
            if (bodyLimit != -1 && (body.length() + buff.length()) > bodyLimit) {
                failed = true;
                context.fail(413);
            } else {
                body.appendBuffer(buff);
            }
        }


        public void doEnd() {
            if (failed || ended) {
                return;
            }
            ended = true;
            context.next();
        }
    }
}
