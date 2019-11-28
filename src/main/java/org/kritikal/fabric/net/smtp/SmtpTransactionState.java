package org.kritikal.fabric.net.smtp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.BufferFactory;

import java.util.Date;

/**
 * Created by ben on 5/14/14.
 */
public class SmtpTransactionState {
    public SmtpTransactionState()
    {
        state = SmtpTransactionStateEnum.INITIAL;
        start = new Date();
        message = new SmtpMessage();
        remainingToRead = Buffer.buffer();
        pos = 0;
        deliveryInformation = null;
        lastResponse = null;
    }
    public SmtpTransactionState(JsonObject o)
    {
        state = SmtpTransactionStateEnum.INITIAL;
        start = new Date();
        message = new SmtpMessage(o);
        remainingToRead = Buffer.buffer();
        pos = 0;
        deliveryInformation = new SmtpDeliveryInformation(o);
        retryCount = o.getInteger("retry_count", 0);
        lastResponse = o.containsKey("last_response") ? Buffer.buffer(o.getString("last_response")) : null;
    }

    public boolean tainted = false;

    public String serverIp = null;
    public String serverName = null;

    public JsonObject toJsonObject()
    {
        JsonObject o = new JsonObject();
        o.put("retry_count", retryCount);
        message.toJsonObject(o);
        if (deliveryInformation != null)
        {
            deliveryInformation.toJsonObject(o);
        }
        if (lastResponse != null)
        {
            o.put("last_response", lastResponse.toString());
        }
        return o;
    }

    public JsonObject toMiniJsonObject()
    {
        JsonObject o = new JsonObject();
        o.put("retry_count", retryCount);
        message.toJsonObject(o);
        deliveryInformation.toMiniJsonObject(o);
        if (lastResponse != null)
        {
            o.put("last_response", lastResponse.toString());
        }
        return o;
    }

    public SmtpDeliveryInformation deliveryInformation;
    public SmtpTransactionStateEnum state;
    public Date start;
    public SmtpMessage message;
    public Buffer remainingToRead;
    public Buffer lastResponse;
    private int pos;
    public int retryCount;

    private void accepted()
    {
        int length = remainingToRead.length();
        if (length > pos)
            remainingToRead = remainingToRead.getBuffer(pos, length-1);
        else
            remainingToRead = Buffer.buffer();
        pos = 0;
    }

    public Buffer readLine() throws Exception
    {
        int length = remainingToRead.length();
        for (;pos<length;)
        {
            byte b = remainingToRead.getByte(pos++);
            if (b == 0x0a) // \n
            {
                try {
                    return remainingToRead.getBuffer(0, pos);
                }
                finally {
                    accepted();
                }
            }
        }
        if (pos > 1024) throw new Exception(); // no > 1024 character lines, please
        return null;
    }

    public Buffer readLines() throws Exception
    {
        lastResponse = readLines_internal();
        return lastResponse;
    }

    private Buffer readLines_internal() throws Exception
    {
        int length = remainingToRead.length();
        for (;pos<length;)
        {
            byte b = remainingToRead.getByte(pos++);
            if (b == 0x0a) // \n
            {
                int i = pos-2;
                for (; i>=0; --i)
                {
                    if (remainingToRead.getByte(i) == 0x0a)
                    {
                        ++i;
                        break;
                    }
                }
                if (i < 0) i = 0;
                if (pos - i > 4)
                {
                    byte b1 = remainingToRead.getByte(i++);
                    byte b2 = remainingToRead.getByte(i++);
                    byte b3 = remainingToRead.getByte(i++);
                    byte b4 = remainingToRead.getByte(i);
                    if (b1 >= 0x30 && b1 <= 0x39 &&
                        b2 >= 0x30 && b2 <= 0x39 &&
                        b3 >= 0x30 && b3 <= 0x39 &&
                        b4 == 0x20) {
                        try {
                            return remainingToRead.getBuffer(0, pos);
                        }
                        finally {
                            accepted();
                        }
                    }
                }
            }
        }
        if (pos > 1024 * 1024) throw new Exception(); // no > 1mb character blocks, please
        return null;
    }

    public Buffer readSmtpData() throws Exception
    {
        int length = remainingToRead.length();
        for (;pos<length;)
        {
            byte b = remainingToRead.getByte(pos++);
            if (b == 0x0a) {
                // \r\n.\r\n
                if (pos >= 5
                        && remainingToRead.getByte(pos - 5) == 0x0d
                        && remainingToRead.getByte(pos - 4) == 0x0a
                        && remainingToRead.getByte(pos - 3) == 0x2e
                        && remainingToRead.getByte(pos - 2) == 0x0d) {
                    try {
                        return remainingToRead.getBuffer(0, pos - 5);
                    } finally {
                        accepted();
                    }
                }
            }
        }
        if (pos > 256 * 1024 * 1024) throw new Exception(); // no 256MB attachments, please
        return null;
    }
}
