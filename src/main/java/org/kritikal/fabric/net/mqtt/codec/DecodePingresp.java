package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.PingRespMessage;

/**
 * Created by ben on 8/24/14.
 */
public class DecodePingresp {
    public static PingRespMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException
    {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        // assert remainingLength == 0
        bufferContainer.skipBytes(remainingLength);
        PingRespMessage message = new PingRespMessage();
        Helper.applyByte1(message, byte1);
        return message;
    }
}
