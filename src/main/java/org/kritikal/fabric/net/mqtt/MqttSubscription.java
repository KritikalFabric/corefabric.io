package org.kritikal.fabric.net.mqtt;

/**
 * Created by ben on 8/25/14.
 */
public class MqttSubscription extends MqttTopic {
    public MqttSubscription(final String topic, final byte qos)
    {
        super(topic);
        this.qos = qos;
    }
    public final byte qos;

    public boolean matches(final MqttTopic topic) {
        return matches(0, 0, topic);
    }

    public boolean isWildcard() {
        for (int i = 0; i < parts.length; ++i)
            if ("+".equals(parts[i])||("#".equals(parts[i])))
                return true;
        return false;
    }

    private boolean matches(int i, int j, final MqttTopic mqttTopic) {
        for (; i < parts.length && j < mqttTopic.parts.length; ++i, ++j)
        {
            if ("+".equals(parts[i])) {
                continue;
            }
            else if ("#".equals(parts[i])) {
                if (i == parts.length - 1)
                    return true;
                for (int x = i + 1; x < parts.length; ++x)
                    for (int y = j; y < mqttTopic.parts.length; ++y) {
                        if (matches(x, y, mqttTopic)) return true;
                    }
            }
            else if (!parts[i].equals(mqttTopic.parts[j])) {
                return false;
            }
        }
        return i == parts.length && j == mqttTopic.parts.length; // remember i or j just overflowed
    }
}
