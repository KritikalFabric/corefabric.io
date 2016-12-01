package org.kritikal.fabric.net.dns.response;

import org.kritikal.fabric.net.dns.DnsQuestionEntry;
import org.kritikal.fabric.net.dns.DnsResponseMessage;

import java.net.InetAddress;

/**
 * Created by ben on 16/03/15.
 */
public class FailDnsResponse implements IDnsResponse {

    public void answerFor(DnsResponseMessage response, DnsQuestionEntry question, InetAddress ignored) {
        response.RCODE = 5; // refused

        response.questions = null;
        response.answers = null;
        response.nameservers = null;
        response.additional = null;
    }

}
