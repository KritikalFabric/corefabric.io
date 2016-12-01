package org.kritikal.fabric.net.dns.response;

import io.vertx.core.buffer.Buffer;
import org.kritikal.fabric.net.dns.DnsQuestionEntry;
import org.kritikal.fabric.net.dns.DnsResourceRecordEntry;
import org.kritikal.fabric.net.dns.DnsResponseMessage;
import org.kritikal.fabric.net.dns.UnsignedConverter;

import java.net.InetAddress;

/**
 * Created by ben on 23/03/2016.
 */
public class MX_DnsResponse implements IDnsResponse {

    public int preference = 5;
    public String host = null;

    public void answerFor(DnsResponseMessage response, DnsQuestionEntry question, InetAddress dummy) {

        try {
            DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

            entry.NAME = question.QNAME;
            entry.TYPE = 15; // MX record
            entry.CLASS = 1; // IN
            entry.TTL = 300; // 5 minutes

            entry.MXFN = (call, buffer) -> {
                Buffer bufferInternal = Buffer.buffer(1500);
                bufferInternal.appendByte((byte)(preference / 256)).appendByte((byte)(preference % 256));
                int offset = call.offset + buffer.length() + 2;
                DnsResourceRecordEntry.writeDomainNameLabelled(host, bufferInternal, call.compression, offset);
                buffer.appendBytes(UnsignedConverter.uint16(bufferInternal.length()));
                buffer.appendBytes(bufferInternal.getBytes());
            };

            response.answers.add(entry);

            response.RCODE = 0;
            response.AA = true;
        }
        catch (Exception e)
        {
            // eat it
        }
    }

}
