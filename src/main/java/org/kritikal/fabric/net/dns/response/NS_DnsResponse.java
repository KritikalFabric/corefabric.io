package org.kritikal.fabric.net.dns.response;

import org.kritikal.fabric.net.dns.DnsQuestionEntry;
import org.kritikal.fabric.net.dns.DnsResourceRecordEntry;
import org.kritikal.fabric.net.dns.DnsResponseMessage;
import org.kritikal.fabric.net.dns.UnsignedConverter;
import io.vertx.core.buffer.Buffer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Created by ben on 17/03/15.
 */
public class NS_DnsResponse implements IDnsResponse {

    public String nameserver = null;
    public ArrayList<InetAddress> nameserverAddresses = new ArrayList<>();

    public void answerFor(DnsResponseMessage response, DnsQuestionEntry question, InetAddress ignored) {

        try {
            InetAddress localhost = Inet6Address.getLocalHost();

            {
                DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

                entry.NAME = question.QNAME;
                entry.TYPE = 2; // NS record
                entry.CLASS = 1; // IN
                entry.TTL = 300; // 5 minutes

                entry.RDFN = (call, buffer) -> {
                    Buffer bufferInternal = Buffer.buffer(1500);
                    int offset = call.offset + buffer.length() + 2;
                    DnsResourceRecordEntry.writeDomainNameLabelled(nameserver, bufferInternal, call.compression, offset);
                    buffer.appendBytes(UnsignedConverter.uint16(bufferInternal.length()));
                    buffer.appendBytes(bufferInternal.getBytes());
                };

                response.answers.add(entry);
            }

            response.RCODE = 0;
            response.AA = true;

            // add the glue to additional information
            for (InetAddress inet : nameserverAddresses) {
                if (inet instanceof Inet4Address) {

                    DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

                    entry.NAME = nameserver;
                    entry.TYPE = 1; // A record
                    entry.CLASS = 1; // IN
                    entry.TTL = 300; // 5 minutes
                    entry.RDATA = inet.getAddress();
                    entry.RDLENGTH = entry.RDATA.length;

                    response.additional.add(entry);

                } else if (inet instanceof Inet6Address) {

                    DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

                    entry.NAME = nameserver;
                    entry.TYPE = 28; // AAAA record
                    entry.CLASS = 1; // IN
                    entry.TTL = 300; // 5 minutes
                    entry.RDATA = new byte[16];
                    byte[] inet6 = inet.getAddress();
                    for (int i = 16 - inet6.length, j = 0, l = 16; i < l; ++i, ++j)
                        entry.RDATA[i] = inet6[j];
                    entry.RDLENGTH = entry.RDATA.length;

                    response.additional.add(entry);

                }
            }
        }
        catch (Exception e)
        {
            // eat it
        }
    }

}