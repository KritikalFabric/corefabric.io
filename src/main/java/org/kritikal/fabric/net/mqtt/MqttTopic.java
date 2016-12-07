package org.kritikal.fabric.net.mqtt;

import java.util.ArrayList;

/**
 * Created by ben on 8/26/14.
 */
public class MqttTopic {
    public MqttTopic(final String topic)
    {
        String[] parts = topic.split("/");
        ArrayList<String> p = new ArrayList<>();
        for (String part : parts)
        {
            if (part == null || "".equals(part))
                continue;
            p.add(part);
        }
        this.parts = new String[p.size()];
        this.topic = String.join("/", this.parts);
        p.toArray(this.parts);
    }
    public final String topic;
    protected final String[] parts;
}
