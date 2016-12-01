package org.kritikal.fabric.net.mqtt.entities;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by ben on 02/02/2016.
 */
public class SubAckMessage extends AbstractMessage {

    public SubAckMessage() { super(AbstractMessage.SUBACK); }

    List<AbstractMessage.QOSType> qosTypes = new ArrayList<>();

    public List<AbstractMessage.QOSType> types() {
        return qosTypes;
    }

    public void addType(AbstractMessage.QOSType qosType) {
        qosTypes.add(qosType);
    }
}
