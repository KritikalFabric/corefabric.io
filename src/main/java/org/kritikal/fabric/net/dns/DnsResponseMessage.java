package org.kritikal.fabric.net.dns;

import org.kritikal.fabric.net.dns.response.IDnsResponse;
import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;

/**
 * Created by ben on 16/03/15.
 */
public class DnsResponseMessage extends DnsMessage {

    public DnsResponseMessage(final DnsQueryMessage queryMessage) {
        super(queryMessage);
        this.QR = true;
        this.questions = queryMessage.questions;
        this.answers = new ArrayList<>();
        this.nameservers = new ArrayList<>();
        this.additional = new ArrayList<>();
    }

}
