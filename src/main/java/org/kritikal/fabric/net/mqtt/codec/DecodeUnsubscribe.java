package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.UnsubscribeMessage;

import java.io.UnsupportedEncodingException;

/**
 * Created by ben on 8/25/14.
 */
public class DecodeUnsubscribe {
    public static UnsubscribeMessage decode(BufferContainer bufferContainer)
        throws BufferContainer.NeedMoreDataException,
            UnsupportedEncodingException,
            Exception
    {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        bufferContainer.assertBytes(remainingLength);
        UnsubscribeMessage message = new UnsubscribeMessage();
        Helper.applyByte1(message, byte1);
        int messageID = bufferContainer.readShort(); remainingLength -= 2;
        message.setMessageID(messageID);
        if (remainingLength < 0) throw new Exception();
        while (remainingLength > 0)
        {
            int strlen = bufferContainer.readShort(); remainingLength -= 2;
            if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
            String string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
            if (remainingLength < 0) throw new Exception();
            message.addTopicFilter(string);
        }
        return message;
    }
}
