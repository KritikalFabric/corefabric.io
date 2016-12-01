package org.kritikal.fabric.net.dns.response;

import org.kritikal.fabric.net.dns.DnsQuestionEntry;
import org.kritikal.fabric.net.dns.DnsResourceRecordEntry;
import org.kritikal.fabric.net.dns.DnsResponseMessage;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Created by ben on 16/03/15.
 */
public class A_DnsResponse implements IDnsResponse {

    public void answerFor(DnsResponseMessage response, DnsQuestionEntry question, InetAddress inetAddress) {

        try {
            DnsResourceRecordEntry entry = new DnsResourceRecordEntry();

            entry.NAME = question.QNAME;
            entry.TYPE = 1; // A record
            entry.CLASS = 1; // IN
            entry.TTL = 300; // 5 minutes
            entry.RDATA = inetAddress.getAddress();
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
