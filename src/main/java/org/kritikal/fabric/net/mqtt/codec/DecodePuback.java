package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.PubAckMessage;

/**
 * Created by ben on 8/25/14.
 */
public class DecodePuback {
    public static PubAckMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException
    {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        // TODO: assert remainingLength == 2
        int messageID = bufferContainer.readShort();
        PubAckMessage message = new PubAckMessage();
        Helper.applyByte1(message, byte1);
        message.setMessageID(Integer.valueOf(messageID));
        return message;
    }
}
