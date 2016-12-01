package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.UnsubAckMessage;

/**
 * Created by ben on 8/25/14.
 */
public class DecodeUnsuback {
    public static UnsubAckMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException
    {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        // TODO: assert remainingLength == 2
        int messageID = bufferContainer.readShort();
        UnsubAckMessage message = new UnsubAckMessage();
        Helper.applyByte1(message, byte1);
        message.setMessageID(messageID);
        return message;
    }
}
