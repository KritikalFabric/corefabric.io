package org.kritikal.fabric.net.smtp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;

/**
 * Created by ben on 5/15/14.
 */
public abstract class SmtpcProtocolController {

    protected final Logger logger;
    protected final SmtpTransactionState state;
    protected final String address;

    public SmtpcProtocolController(final SmtpTransactionState state, final Logger logger, final String address)
    {
        this.state = state;
        this.logger = logger;
        this.address = address;
    }

    public final void handleSubmission()
    {
        StringBuilder warn = new StringBuilder();
        warn.append(address).append(" SUBMITTED ").append(" from=<").append(state.message.from);
        for (int i = 0; i < state.message.to.size(); ++i)
        {
            warn.append("> to=<").append(state.message.to.get(i));
        }
        warn.append(">");
        logger.warn(warn.toString());
        handleSuccess(state);
    }

    // no message
    public abstract void handleSuccess(final SmtpTransactionState state);

    // full message, with delivery information but not state.
    public abstract void handleRetry(final SmtpTransactionState state, final JsonObject o);

    // partial message, just one to address.
    public abstract void handleUndeliverable(final SmtpTransactionState state, final JsonObject o);

    public final void handleProtocolFailure(Buffer buffer)
    {
        String data = null;
        int code = 400;
        if (buffer == null || buffer.length() < 3)
        {
            data = "400 misc error\r\n";
            code = 400;
        }
        else
        {
            data = buffer.toString(Constants.ASCII);

            try {
                code = Integer.parseInt(buffer.getString(0, 3, Constants.ASCII));
            }
            catch (Exception ex)
            {
                code = 400;
            }
        }

        state.retryCount++;
        if ((400 <= code && code < 500) && state.retryCount < 3) {
            final JsonObject o = state.toMiniJsonObject();
            handleRetry(state, o);
        }
        else {
            StringBuilder fatal = new StringBuilder();
            fatal.append(address).append(" *FAILURE* ").append(" from=<").append(state.message.from);
            for (int i = 0; i < state.message.to.size(); ++i) {
                fatal.append("> to=<").append(state.message.to.get(i));
            }
            fatal.append(">");
            logger.fatal(fatal.toString());

            // Generate bounce.

            StringBuilder bounceMessage = new StringBuilder();
            bounceMessage.append("Diagnostic-Code: smtp; ");
            int i = data.indexOf('\r');
            int j = data.indexOf('\n');
            int k = 0;
            if (i >= 0 && j >= 0) {
                k = Math.min(i, j);
            } else if (i >= 0) {
                k = i;
            } else {
                k = j;
            }
            String diagnosticCode = k >= 0 ? data.substring(0, k) : "";
            if (diagnosticCode.length() > 3 && diagnosticCode.charAt(3) == '-') {
                diagnosticCode = diagnosticCode.substring(0, 3) + " " + diagnosticCode.substring(4);
            }
            bounceMessage.append(diagnosticCode).append("\r\n");
            bounceMessage.append("Subject: Permanent Delivery Failure\r\n");
            bounceMessage.append("\r\n");
            bounceMessage.append(data);

            final JsonObject o = new JsonObject();
            JsonArray to = new JsonArray();
            to.add(state.message.from);
            o.put("to", to);
            o.put("from", "");
            o.put("body", bounceMessage.toString());

            handleUndeliverable(state, o);
        }
    }
}
