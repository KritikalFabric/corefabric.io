package org.kritikal.fabric.net.mqtt.entities;

import org.kritikal.fabric.net.mqtt.MqttTopic;

import java.util.UUID;

/**
 * Created by ben on 02/02/2016.
 */
public class PublishMessage extends AbstractMessage {

    public PublishMessage() { super(AbstractMessage.PUBLISH); }

    String topicName = null;
    MqttTopic topic = null;

    public String getTopicName() {
        return topicName;
    }

    public MqttTopic getTopic() { return topic; }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
        this.topic = new MqttTopic(topicName);
    }

    // extensions

    public long expires = 0l;
    public String origin = "";
}
