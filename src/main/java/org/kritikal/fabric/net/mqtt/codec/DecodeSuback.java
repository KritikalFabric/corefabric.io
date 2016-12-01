package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.SubAckMessage;

/**
 * Created by ben on 8/24/14.
 */
public class DecodeSuback {
    public static SubAckMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException
    {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        // TODO read QoS levels if we actually care about that :-)
        bufferContainer.skipBytes(remainingLength);
        SubAckMessage message = new SubAckMessage();
        Helper.applyByte1(message, byte1);
        return message;
    }
}
