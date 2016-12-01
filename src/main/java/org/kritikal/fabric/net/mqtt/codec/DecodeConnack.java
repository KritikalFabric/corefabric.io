package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.mqtt.entities.*;
import org.kritikal.fabric.net.BufferContainer;

/**
 * Created by ben on 8/24/14.
 */
public class DecodeConnack {
    public static ConnAckMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException
    {
        byte byte1 = bufferContainer.readByte(); // assert equal to 0x20
        byte byte2 = bufferContainer.readByte(); // assert equal to 2
        byte byte3 = bufferContainer.readByte(); // reserved, ignore
        byte byte4 = bufferContainer.readByte(); // return code

        ConnAckMessage message = new ConnAckMessage();
        Helper.applyByte1(message, byte1);
        message.setReturnCode(byte4);
        return message;
    }
}
