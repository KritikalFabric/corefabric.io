package org.kritikal.fabric.net.smtp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Created by ben on 5/15/14.
 */
public abstract class SmtpdProtocol {
    public static Pattern emailPattern = Pattern.compile("<([^>]*)>");

    final NetSocket socket;
    public final SmtpTransactionState state = new SmtpTransactionState();
    public SmtpdProtocol(final NetSocket socket)
    {
        this.socket = socket;
        socket.write("220 " + banner() + " SMTP\r\n");
        state.state = SmtpTransactionStateEnum.HELO;
    }

    public SocketAddress getRemoteAddress() {
        return socket.remoteAddress();
    }

    public abstract String banner();

    public abstract void handleRset();

    public abstract void handleClose();

    public boolean validFrom(String emailAddress) { return true; }

    public boolean validTo(String emailAddress) { return true; }

    public abstract void handleMessage();

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

    public final void recvInitial()
    {
        socket.write("500 Protocol error.\r\n");
        socket.close();
    }

    Buffer readLine()
    {
        try
        {
            return state.readLine();
        }
        catch (Exception ex)
        {
            socket.write("500 " + ex.getMessage() + "\r\n");
            socket.close();
            return null;
        }
    }

    Buffer readSmtpData()
    {
        try
        {
            return state.readSmtpData();
        }
        catch (Exception ex)
        {
            socket.write("500 " + ex.getMessage() + "\r\n");
            socket.close();
            return null;
        }
    }

    final void recvHelo()
    {
        Buffer line = readLine();
        if (line != null) {
            if (line.length() > 5) {
                for (int i = 0; i < 5; ++i) {
                    byte b = line.getByte(i);
                    if (b >= 0x61 && b <= 0x7A) {
                        line.setByte(i, (byte) (b - 0x20));
                    }
                }
                final String s = line.getString(0, 5, Constants.ASCII);
                if ("HELO ".equals(s)) {
                    socket.write("250 Pleased to meet you\r\n");
                    state.state = SmtpTransactionStateEnum.MAIL_FROM;
                } else if ("EHLO ".equals(s)) {
                    socket.write("250 Delighted to make your acquaintance\r\n");
                    state.state = SmtpTransactionStateEnum.MAIL_FROM;
                } else if (s.startsWith("QUIT")) {
                    socket.write("221 TTFN\r\n");
                    socket.close();
                } else {
                    protocolSarcasm(s);
                }
            } else {
                socket.write("500 Protocol error.\r\n");
                socket.close();
            }
        }
    }

    final void recvMailFrom()
    {
        Buffer line = readLine();
        if (line != null) {
            String s = null;
            if (line.length() >= 10) {
                for (int i = 0; i < 10; ++i) {
                    byte b = line.getByte(i);
                    if (b >= 0x61 && b <= 0x7A) {
                        line.setByte(i, (byte) (b - 0x20));
                    }
                }
                s = line.getString(0, 10, Constants.ASCII);
            } else if (line.length() >= 4) {
                for (int i = 0; i < 4; ++i) {
                    byte b = line.getByte(i);
                    if (b >= 0x61 && b <= 0x7A) {
                        line.setByte(i, (byte) (b - 0x20));
                    }
                }
                s = line.getString(0, 4, Constants.ASCII);
            } else {
                socket.write("500 Protocol error.\r\n");
                socket.close();
            }
            if (s != null) {
                if ("MAIL FROM:".equals(s)) {
                    String wholeLine = line.getString(0, line.length()-1, Constants.ASCII);
                    Matcher m = emailPattern.matcher(wholeLine);
                    if (m.find()) {
                        final String emailAddress = m.group(1);
                        if (validFrom(emailAddress)) {
                            socket.write("250 OK <" + emailAddress + ">\r\n");
                            state.message.from = emailAddress;
                            state.state = SmtpTransactionStateEnum.RCPT_TO;
                        } else {
                            socket.write("550 Sender rejected\r\n");
                        }
                    }
                    else {
                        socket.write("550 Invalid input\r\n");
                    }
                } else if (s.startsWith("QUIT")) {
                    socket.write("221 TTFN\r\n");
                    socket.close();
                } else if (s.startsWith("RSET")) {
                    socket.write("250 OK\r\n");
                    state.message = new SmtpMessage();
                    state.state = SmtpTransactionStateEnum.MAIL_FROM;
                    handleRset();
                } else {
                    protocolSarcasm(s);
                }
            }
        }
    }

    final void recvRcptTo()
    {
        Buffer line = readLine();
        if (line != null) {
            String s = null;
            if (line.length() >= 8) {
                for (int i = 0; i < 8; ++i) {
                    byte b = line.getByte(i);
                    if (b >= 0x61 && b <= 0x7A) {
                        line.setByte(i, (byte) (b - 0x20));
                    }
                }
                s = line.getString(0, 8, Constants.ASCII);
            } else if (line.length() >= 4) {
                for (int i = 0; i < 4; ++i) {
                    byte b = line.getByte(i);
                    if (b >= 0x61 && b <= 0x7A) {
                        line.setByte(i, (byte) (b - 0x20));
                    }
                }
                s = line.getString(0, 4, Constants.ASCII);
            } else {
                socket.write("500 Protocol error.\r\n");
                socket.close();
            }
            if (s != null) {
                if ("RCPT TO:".equals(s)) {
                    String wholeLine = line.getString(0, line.length()-1, Constants.ASCII);
                    Matcher m = emailPattern.matcher(wholeLine);
                    if (m.find()) {
                        final String emailAddress = m.group(1);
                        if (validTo(emailAddress)) {
                            socket.write("250 OK <" + emailAddress + ">\r\n");
                            state.message.to.add(emailAddress);
                        } else {
                            socket.write("554 Recipient rejected\r\n");
                        }
                    } else {
                        socket.write("554 Invalid input\r\n");
                    }
                } else if (s.startsWith("QUIT")) {
                    socket.write("221 TTFN\r\n");
                    socket.close();
                } else if (s.startsWith("RSET")) {
                    socket.write("250 OK\r\n");
                    state.message = new SmtpMessage();
                    state.state = SmtpTransactionStateEnum.MAIL_FROM;
                    handleRset();
                } else if (s.startsWith("DATA")) {
                    if (state.message.to.size() > 0) {
                        socket.write("354 OK\r\n");
                        state.state = SmtpTransactionStateEnum.DATA;
                    } else {
                        socket.write("500 Protocol error.\r\n");
                        socket.close();
                    }
                } else {
                    protocolSarcasm(s);
                }
            }
        }
    }

    final void recvData()
    {
        Buffer buffer = readSmtpData();
        if (buffer != null) {
            socket.write("250 Queued\r\n");
            state.message.setBuffer(buffer);
            handleMessage();
            state.message = new SmtpMessage();
            state.state = SmtpTransactionStateEnum.MAIL_FROM;
        }
    }

    final void protocolSarcasm(String uLine) {
        if (uLine.startsWith("NOOP")) {
            socket.write("250 Ok\r\n");
        } else if (uLine.startsWith("HELP")) {
            socket.write("250 Google can help you learn the SMTP protocol.\r\n");
        } else if (uLine.startsWith("VRFY")) {
            socket.write("550 Do you think that I would tell you that?\r\n");
        } else if (uLine.startsWith("TURN")) {
            socket.write("550 I can't do that.\r\n");
        } else if (uLine.startsWith("ETRN")) {
            socket.write("550 I can't do that either.\r\n");
        } else if (uLine.startsWith("EXPN")) {
            socket.write("550 I'm sorry, Dave, but I cannot allow you to do that.\r\n");
        } else if (uLine.startsWith("SEND") || uLine.startsWith("SOML") || uLine.startsWith("SAML")) {
            socket.write("550 Sorry, this server is headless.\r\n");
        } else {
            socket.write("500 Protocol error.\r\n");
            socket.close();
        }
    }
}
