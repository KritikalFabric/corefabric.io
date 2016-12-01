package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.PingReqMessage;

/**
 * Created by ben on 8/25/14.
 */
public class DecodePingreq {
    public static PingReqMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException, Exception
    {
        byte byte1 = bufferContainer.readByte();
        PingReqMessage message = new PingReqMessage();
        Helper.applyByte1(message, byte1);
        long remainingLength = bufferContainer.readRemainingLength();
        if (remainingLength != 0) throw new Exception(); // assert equal to zero
        return message;
    }
}
