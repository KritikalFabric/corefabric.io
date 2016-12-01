package org.kritikal.fabric.net.mqtt.entities;

/**
 * Created by ben on 02/02/2016.
 */
public class UnsubAckMessage extends AbstractMessage {

    public UnsubAckMessage() { super(AbstractMessage.UNSUBACK); }
}
