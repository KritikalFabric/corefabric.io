package org.kritikal.fabric.net.http;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.impl.FileUploadImpl;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by ben on 7/11/16.
 */
/**
 * modified from vert.x sources; see their copyright
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileUploadHandler implements BodyHandler {

    private static final Logger log = LoggerFactory.getLogger(FileUploadHandler.class);

    private static final String BODY_HANDLED = "__body-handled";

    private final BiConsumer<RoutingContext, HttpServerFileUpload> onUpload;

    public FileUploadHandler(BiConsumer<RoutingContext, HttpServerFileUpload> onUpload) {
        this.onUpload = onUpload;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        // we need to keep state since we can be called again on reroute
        Boolean handled = context.get(BODY_HANDLED);
        if (handled == null || !handled) {
            BHandler handler = new BHandler(context);
            request.handler(handler);
            request.endHandler(v -> handler.end());
            context.put(BODY_HANDLED, true);
        } else {
            // on reroute we need to re-merge the form params if that was desired
            if (request.isExpectMultipart()) {
                request.params().addAll(request.formAttributes());
            }
            context.next();
        }
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
    public BodyHandler setMergeFormAttributes(boolean mergeFormAttributes) {
        return this;
    }

    @Override
    public BodyHandler setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd) {
        return this;
    }

    @Override
    public BodyHandler setHandleFileUploads(boolean handleFileUploads) {
        return this;
    }

    @Override
    public BodyHandler setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer) {
        return this;
    }

    private class BHandler implements Handler<Buffer> {

        RoutingContext context;
        Buffer body = Buffer.buffer();
        boolean failed;
        AtomicInteger uploadCount = new AtomicInteger();
        boolean ended;
        long uploadSize = 0L;

        final boolean isMultipart;
        final boolean isUrlEncoded;

        public BHandler(RoutingContext context) {
            this.context = context;
            Set<FileUpload> fileUploads = context.fileUploads();

            final String contentType = context.request().getHeader(HttpHeaders.CONTENT_TYPE);
            isMultipart = contentType != null && contentType.contains("multipart/form-data");
            isUrlEncoded = contentType != null && contentType.contains("application/x-www-form-urlencoded");
            if (isMultipart)
            {
                context.request().setExpectMultipart(true);
                context.request().uploadHandler(upload -> {
                    // we actually upload to a file with a generated filename
                    uploadCount.incrementAndGet();
                    FileUploadImpl fileUpload = new FileUploadImpl(upload.filename(), upload);
                    fileUploads.add(fileUpload);
                    upload.exceptionHandler(context::fail);
                    upload.endHandler(v -> uploadEnded());
                    onUpload.accept(context, upload);
                });
            }
            context.request().exceptionHandler(context::fail);
        }

        @Override
        public void handle(Buffer buff) {
            if (failed) {
                return;
            }
            uploadSize += buff.length();
            //if (bodyLimit != -1 && uploadSize > bodyLimit) {
             //   failed = true;
             //   context.fail(413);
            //} else {
                if (!isMultipart && !isUrlEncoded) {
                    body.appendBuffer(buff);
                }
            //}
        }

        void uploadEnded() {
            int count = uploadCount.decrementAndGet();
            // only if parsing is done and count is 0 then all files have been processed
            if (ended && count == 0) {
                doEnd();
            }
        }

        void end() {
            // this marks the end of body parsing, calling doEnd should
            // only be possible from this moment onwards
            ended = true;

            // only if parsing is done and count is 0 then all files have been processed
            if (uploadCount.get() == 0) {
                doEnd();
            }
        }

        void doEnd() {
            if (failed) {
                return;
            }

            HttpServerRequest req = context.request();
            if (req.isExpectMultipart()) {
                req.params().addAll(req.formAttributes());
            }
            context.setBody(body);
            context.next();
        }
    }
}