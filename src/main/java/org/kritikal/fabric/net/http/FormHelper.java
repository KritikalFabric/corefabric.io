package org.kritikal.fabric.net.http;

import org.apache.commons.codec.net.URLCodec;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ben on 4/13/16.
 */
public class FormHelper {

    public static Map<String, String> getQueryMap(String query)
    {
        URLCodec codec = new URLCodec();
        Map<String, String> map = new HashMap<String, String>();
        if (null != query && !"".equals(query)) {
            String[] params = query.split("&");
            for (String param : params) {
                int eq = param.indexOf('=');
                String name = param.substring(0, eq);
                String value = null;
                try {
                    value = codec.decode(param.substring(eq + 1));
                } catch (Exception e) {
                    value = null;
                }
                map.put(name, value);
            }
        }
        return map;
    }

}
