package org.kritikal.fabric.net.mqtt;

import java.util.ArrayList;

/**
 * Created by ben on 8/26/14.
 */
public class MqttTopic {
    public MqttTopic(final String topic)
    {
        this.topic = topic;
        String[] parts = topic.split("/");
        ArrayList<String> p = new ArrayList<>();
        for (String part : parts)
        {
            if (part == null || "".equals(part))
                continue;
            p.add(part);
        }
        this.parts = new String[p.size()];
        p.toArray(this.parts);
    }
    public final String topic;
    protected final String[] parts;
}
