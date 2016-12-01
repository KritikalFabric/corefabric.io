package org.kritikal.fabric.net.dns;

import io.vertx.core.buffer.Buffer;

import java.util.function.BiConsumer;

/**
 * Created by ben on 16/03/15.
 */
public class DnsResourceRecordEntry {

    public int populateFrom(byte[] data, int start) {
        int i = start;

        StringBuilder sb = new StringBuilder();
        i = UnsignedConverter.parseDomainNameFrom(sb, data, i);
        NAME = sb.toString();

        TYPE = UnsignedConverter.uint16(data, i++); ++i;
        CLASS = UnsignedConverter.uint16(data, i++); ++i;
        TTL = UnsignedConverter.uint32(data, i); i += 4;
        RDLENGTH = UnsignedConverter.uint16(data, i);
        RDATA = new byte[RDLENGTH];
        for (int j = 0; j < RDLENGTH; ++j, ++i)
            RDATA[j] = data[i];

        return i;
    }

    public String NAME;
    public int TYPE;
    public int CLASS;
    public long TTL; // 32-bit
    public int RDLENGTH;
    public byte[] RDATA;
    public BiConsumer<DnsResponseCompressionCall, Buffer> RDFN;
    public BiConsumer<DnsResponseCompressionCall, Buffer> MXFN;

    public static void writeDomainNameLabelled(String name, Buffer buffer, DnsResponseCompressionState compression, int offset) {
        String[] parts = name.split("\\."); // TODO: compile regex
        boolean lastWasPointer = false;
        for (int i = 0, l = parts.length; i < l; ++i)
        {
            if (parts[i] == null || parts[i].length() == 0) {
                break;
            }

            StringBuilder remainder = new StringBuilder();
            for (int j = i; j < l; ++j) {
                if (parts[j] == null || parts[j].length() == 0) {
                    break;
                }
                remainder.append(parts[j]).append('.');
            }
            String dnsName = remainder.toString();

            int ptr = compression.findResponse(dnsName);
            if (ptr != 0) {
                buffer.appendBytes(UnsignedConverter.uint16(ptr)); // back-reference
                lastWasPointer = true;
                break;
            } else {
                ptr = offset + buffer.length() + 4;
                buffer.appendByte((byte) parts[i].length());
                for (char ch : parts[i].toCharArray()) {
                    buffer.appendByte((byte) ch);
                }
                compression.storeResponse(ptr, dnsName);
            }
        }
        if (!lastWasPointer)
            buffer.appendByte((byte) 0);
    }

    public Buffer getBuffer(DnsResponseCompressionState compression, int offset) {
        Buffer buffer = Buffer.buffer(1500);
        writeDomainNameLabelled(NAME, buffer, compression, offset);

        buffer.appendBytes(UnsignedConverter.uint16(TYPE));
        buffer.appendBytes(UnsignedConverter.uint16(CLASS));
        buffer.appendBytes(UnsignedConverter.uint32(TTL));

        if (MXFN != null) {
            MXFN.accept(new DnsResponseCompressionCall(compression, offset), buffer);
        } else if (RDFN != null) {
            RDFN.accept(new DnsResponseCompressionCall(compression, offset), buffer);
        } else {
            buffer.appendBytes(UnsignedConverter.uint16(RDLENGTH));
            buffer.appendBytes(RDATA);
        }

        return buffer;
    }

}
