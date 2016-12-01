package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.ConnectMessage;

import java.io.UnsupportedEncodingException;

/**
 * Created by ben on 8/25/14.
 */
public class DecodeConnect {
    public static ConnectMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException,
            UnsupportedEncodingException,
            Exception
    {
        byte byte1 = bufferContainer.readByte();
        ConnectMessage message = new ConnectMessage();
        Helper.applyByte1(message, byte1);
        long remainingLength = bufferContainer.readRemainingLength();
        bufferContainer.assertBytes(remainingLength);
        int protocolNameLength = bufferContainer.readShort();
        byte[] protocolNameUtf8 = bufferContainer.readBytes(protocolNameLength);
        byte protocolVersion = bufferContainer.readByte();
        byte connectFlags = bufferContainer.readByte();
        int keepAliveTimer = bufferContainer.readShort();
        remainingLength -= 6 + protocolNameLength;

        message.setProtocolName(new String(protocolNameUtf8, "UTF-8"));
        message.setProtocolVersion(protocolVersion);

        message.setCleanSession((connectFlags & 0b10) != 0);
        message.setWillFlag((connectFlags & 0b100) != 0);
        message.setWillQos((byte)((connectFlags & 0b11000) >> 3));
        message.setWillRetain((connectFlags & 0b100000) != 0);
        message.setPasswordFlag((connectFlags & 0b1000000) != 0);
        message.setUserFlag((connectFlags & 0b10000000) != 0);

        message.setKeepAlive(keepAliveTimer);

        int strlen = bufferContainer.readShort(); remainingLength -= 2;
        if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
        String string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
        if (remainingLength < 0) throw new Exception();
        message.setClientID(string);

        if (message.isWillFlag())
        {
            strlen = bufferContainer.readShort(); remainingLength -= 2;
            if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
            string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
            if (remainingLength < 0) throw new Exception();
            message.setWillTopic(string);

            strlen = bufferContainer.readShort(); remainingLength -= 2;
            if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
            string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
            if (remainingLength < 0) throw new Exception();
            message.setWillMessage(string.getBytes());
        }

        if (message.isUserFlag())
        {
            strlen = bufferContainer.readShort(); remainingLength -= 2;
            if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
            string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
            if (remainingLength < 0) throw new Exception();
            message.setUsername(string);
        }

        if (message.isPasswordFlag())
        {
            strlen = bufferContainer.readShort(); remainingLength -= 2;
            if (remainingLength < 0 || strlen > remainingLength) throw new Exception();
            string = new String(bufferContainer.readBytes(strlen), "UTF-8"); remainingLength -= strlen;
            if (remainingLength < 0) throw new Exception();
            message.setPassword(string.getBytes());
        }

        return message;
    }
}
