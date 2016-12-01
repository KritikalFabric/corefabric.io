package org.kritikal.fabric.net.dns;

import java.util.HashMap;

/**
 * Created by ben on 17/03/15.
 */
public class DnsResponseCompressionState {

    public HashMap<String, Integer> map = new HashMap<>();

    public int findResponse(String domainName) {
        Integer i = map.get(domainName);
        if (i == null) return 0;
        return (i & 0b00111111111111111) ^ 0b1100000000000000;
    }

    public void storeResponse(int start, String domainName) {
        map.put(domainName, start);
    }
}
