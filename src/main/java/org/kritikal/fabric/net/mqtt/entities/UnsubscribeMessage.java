package org.kritikal.fabric.net.mqtt.entities;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by ben on 02/02/2016.
 */
public class UnsubscribeMessage extends AbstractMessage {

    public UnsubscribeMessage() { super(AbstractMessage.UNSUBSCRIBE); }

    List<String> _topicFilters = new ArrayList<>();

    public void addTopicFilter(String topicFilter) {
        _topicFilters.add(topicFilter);
    }

    public List<String> topicFilters() {
        return _topicFilters;
    }
}
