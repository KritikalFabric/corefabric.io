package org.kritikal.fabric.net.dns.response;

import org.kritikal.fabric.net.dns.DnsQuestionEntry;
import org.kritikal.fabric.net.dns.DnsResourceRecordEntry;
import org.kritikal.fabric.net.dns.DnsResponseMessage;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Created by ben on 17/03/15.
 */
public class AAAA_DnsResponse implements IDnsResponse {

    public void answerFor(DnsResponseMessage response, DnsQuestionEntry question, InetAddress inetAddress) {

        try {
            DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

            entry.NAME = question.QNAME;
            entry.TYPE = 28; // AAAA record
            entry.CLASS = 1; // IN
            entry.TTL = 300; // 5 minutes
            entry.RDATA = new byte[16];
            byte[] inet6 = inetAddress.getAddress();

            if (inet6.length != 16) throw new Error();

            for (int i = 16 - inet6.length, j = 0, l = 16; i < l; ++i, ++j)
                entry.RDATA[i] = inet6[j];
            entry.RDLENGTH = entry.RDATA.length;

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