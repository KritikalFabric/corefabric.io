package org.kritikal.fabric.net.smtp;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.kritikal.fabric.CoreFabric;

import java.util.ArrayList;

/**
 * Created by ben on 5/15/14.
 */
public abstract class SmtpcProtocol {
    final NetSocket socket;
    public final SmtpTransactionState state;

    String from = null;
    ArrayList<String> to = new ArrayList<String>();
    Buffer body;
    boolean isEsmtp = false;
    boolean supportsSTARTTLS = false;

    public SmtpcProtocol(final NetSocket socket, final SmtpTransactionState state, final String serverIp, final String serverName)
    {
        this.socket = socket;
        this.state = state;
        this.state.serverIp = serverIp;
        this.state.serverName = serverName;

        from = this.state.message.from;
        for (int i = 0; i < this.state.message.to.size(); ++i) { to.add(this.state.message.to.get(i)); }
        body = this.state.message.getBodyBuffer();
    }

    public abstract void handleSentOk();

    public abstract void handleFailure(Buffer lastError);

    public abstract void handleClose();

    Buffer readLines()
    {
        try
        {
            return state.readLines();
        }
        catch (Exception ex)
        {
            handleFailure(null);
            socket.write("500 " + ex.getMessage() + "\r\n");
            socket.close();
            return null;
        }
    }

    public final void appendBuffer(Buffer buffer)
    {
        state.remainingToRead.appendBuffer(buffer);
        switch (state.state)
        {
            case INITIAL:
                recvInitial();
                break;

            case HELO:
                recvHelo();
                break;

            case STARTTLS:
                recvStarttls();
                break;

            case MAIL_FROM:
                recvMailFrom();
                break;

            case RCPT_TO:
                recvRcptTo();
                break;

            case DATA:
                recvData();
                break;
        }
    }

    final void recvInitial()
    {
        // Expect banner, e.g. 220 Hello I'm a friendly mail server
        Buffer lines = readLines();
        if (lines != null)
        {
            if (lines.length() > 4)
            {
                String s = lines.getString(0, 4, Constants.ASCII);
                if ("220 ".equals(s))
                {
                    String banner = lines.toString(Constants.ASCII);
                    isEsmtp = banner.contains(" ESMTP ");

                    if (isEsmtp) {
                        socket.write("EHLO " + CoreFabric.ServerConfiguration.hostname + "\r\n");
                    }
                    else {
                        socket.write("HELO " + CoreFabric.ServerConfiguration.hostname + "\r\n");
                    }
                    state.state = SmtpTransactionStateEnum.HELO;
                }
                else
                {
                    handleFailure(lines);
                    socket.close();
                }
            }
            else
            {
                handleFailure(lines);
                socket.close();
            }
        }
    }

    final void recvHelo()
    {
        Buffer lines = readLines();
        if (lines != null)
        {
            if (lines.length() > 3)
            {
                String s = lines.getString(0, 3, Constants.ASCII);
                if ("250".equals(s))
                {
                    String heloResponse = lines.toString(Constants.ASCII);
                    if (heloResponse.contains("250-STARTTLS") ||
                        heloResponse.contains("250 STARTTLS"))
                    {
                        supportsSTARTTLS = true;
                    }

                    if (supportsSTARTTLS)
                    {
                        socket.write("STARTTLS\r\n");
                        state.state = SmtpTransactionStateEnum.STARTTLS;
                    }
                    else {
                        // Ready
                        socket.write("MAIL FROM:<" + from + ">\r\n");
                        state.state = SmtpTransactionStateEnum.MAIL_FROM;
                    }
                }
                else
                {
                    handleFailure(lines);
                    socket.close();
                }
            }
            else
            {
                handleFailure(lines);
                socket.close();
            }
        }
    }

    final void recvStarttls()
    {
        Buffer lines = readLines();
        if (lines != null) {
            if (lines.length() > 3) {
                String s = lines.getString(0, 3, Constants.ASCII);
                if ("220".equals(s)) {
                    socket.upgradeToSsl(new Handler<Void>() {
                        @Override
                        public void handle(Void event) {
                            if (socket.isSsl()) {
                                socket.write("MAIL FROM:<" + from + ">\r\n");
                                state.state = SmtpTransactionStateEnum.MAIL_FROM;
                            }
                            else {
                                handleFailure(Buffer.buffer("500 during STARTTLS", Constants.ASCII));
                            }
                        }
                    });
                }
                else
                {
                    handleFailure(Buffer.buffer("500 could not STARTTLS", Constants.ASCII));
                }
            }
        }
    }

    final void recvMailFrom()
    {
        Buffer lines = readLines();
        if (lines != null)
        {
            if (lines.length() > 3)
            {
                String s = lines.getString(0, 3, Constants.ASCII);
                if ("250".equals(s))
                {
                    if (to.isEmpty())
                    {
                        socket.write("DATA\r\n");
                        state.state = SmtpTransactionStateEnum.DATA;
                    }
                    else
                    {
                        final String to = this.to.get(0); this.to.remove(0);
                        socket.write("RCPT TO:<" + to + ">\r\n");
                        state.state = SmtpTransactionStateEnum.RCPT_TO;
                    }
                }
                else
                {
                    handleFailure(lines);
                    socket.close();
                }
            }
        }
    }

    final void recvRcptTo()
    {
        recvMailFrom();
    }

    final void recvData()
    {
        Buffer lines = readLines();
        if (lines != null)
        {
            if (lines.length() > 3)
            {
                if (body != null) {
                    String s = lines.getString(0, 3, Constants.ASCII);
                    if ("354".equals(s)) {
                        final Buffer body = this.body;
                        this.body = null;
                        socket.write(body);
                        socket.write("\r\n.\r\n");
                    }
                    else
                    {
                        handleFailure(lines);
                        socket.close();
                    }
                } else {
                    String s = lines.getString(0, 3, Constants.ASCII);
                    if ("250".equals(s)) {
                        state.state = SmtpTransactionStateEnum.HELO;
                        state.message.submitted = true;
                        handleSentOk();
                    }
                    else
                    {
                        handleFailure(lines);
                        socket.close();
                    }
                }
            }
        }
    }
}
