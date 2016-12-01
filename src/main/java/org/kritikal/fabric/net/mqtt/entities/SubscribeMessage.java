package org.kritikal.fabric.net.mqtt.entities;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by ben on 02/02/2016.
 */
public class SubscribeMessage extends AbstractMessage {

    public SubscribeMessage() { super(AbstractMessage.SUBSCRIBE); }

    public static class Couple
    {
        public Couple(byte qos, String topic) {
            this.qos = qos;
            this.topic = topic;
        }

        public byte qos;
        public String topic;

        public byte getQos() { return qos; }
        public String getTopicFilter() { return topic; }
    }

    List<Couple> _subscriptions = new ArrayList<>();

    public List<Couple> subscriptions() {
        return _subscriptions;
    }

    public void addSubscription(Couple subscription) {
        _subscriptions.add(subscription);
    }
}
