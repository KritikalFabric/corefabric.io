package org.kritikal.fabric.net.dns;

/**
 * Created by ben on 17/03/15.
 */
public class DnsResponseCompressionCall {

    public DnsResponseCompressionCall(DnsResponseCompressionState compression, int offset) {
        this.compression = compression;
        this.offset = offset;
    }

    public final DnsResponseCompressionState compression;
    public final int offset;

}
