package org.kritikal.fabric.net.dns;

import io.vertx.core.buffer.Buffer;

/**
 * Created by ben on 16/03/15.
 */
public class DnsQuestionEntry {

    public int populateFrom(byte[] data, int start)
    {
        int i = start;

        StringBuilder sb = new StringBuilder();
        while (data[i] != 0) {
            int l = data[i++];
            while (l-- > 0) {
                sb.append((char)data[i++]);
            }
            sb.append('.');
        }
        ++i;
        QNAME = sb.toString();
        QTYPE = UnsignedConverter.uint16(data, i++); ++i;
        QCLASS = UnsignedConverter.uint16(data, i++); ++i;
        return i;
    }

    public String QNAME;
    public int QTYPE;
    public int QCLASS;

    public Buffer getBuffer()
    {
        Buffer buffer = Buffer.buffer(1500);
        for (String part : QNAME.split("\\.")) { // TODO: compile regex
            if (part == null || part.length() == 0) break;
            buffer.appendByte((byte)part.length());
            for (char ch : part.toCharArray())
            {
                buffer.appendByte(((byte)(ch & 255)));
            }
        }
        buffer.appendByte((byte)0);
        buffer.appendBytes(UnsignedConverter.uint16(QTYPE));
        buffer.appendBytes(UnsignedConverter.uint16(QCLASS));
        return buffer;
    }
}
