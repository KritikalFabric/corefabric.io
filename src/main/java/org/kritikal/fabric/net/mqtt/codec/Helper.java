package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.entities.AbstractMessage;

/**
 * Created by ben on 8/24/14.
 */
public class Helper {

    //private static final int MAXLEN = 268435455;

    /**
     * encode a string as a utf8 byte sequence prefixed with length,
     * as is the requirement of mqtt.
     *
     * it is the responsibility of the caller of this method to .release() the
     * returned ByteBuf
     * @param s string to encode
     * @return a new ByteBuf containing the utf-8 prefixed with length string
     */
    protected static ByteBuf encodeUtf8(String s) {
        ByteBuf output = Unpooled.buffer();
        if (s == null) {
            output.writeShort(0);
            return output;
        }
        byte[] data = null;
        try
        {
            data = s.getBytes("UTF-8");
        } catch (Exception ex) { /* should never happen */ }
        if (data == null) return output;
        output.writeShort(data.length);
        output.writeBytes(data);
        return output;
    }

    /**
     * encode a number (remaining length) as a 1-4 byte buffer.
     *
     * it is the responsibility of the caller of this method to .release() the
     * returned ByteBuf
     * @param len length, out of bounds values are truncated
     * @return a new ByteBuf
     */
    protected static ByteBuf encodeRemainingLength(long len)
    {
        if (len < 0) len = 0;
        //if (len > MAXLEN) len = MAXLEN; // protocol hack, we don't care of the length of this thing
        ByteBuf output = Unpooled.buffer();
        do {
            byte digit = (byte) (len % 0x80);
            len = len / 0x80;
            if (len > 0)
                digit |= 0x80;
            output.writeByte(digit);
        } while (len > 0);
        return output;
    }

    /**
     * encode the first byte of the header with general use information.
     *
     * it is the responsibility of the caller of this method to .release() the
     * returned ByteBuf
     * @param message
     * @return a new ByteBuf
     */
    protected static byte block1(AbstractMessage message)
    {
        int b = message.getMessageType() << 4;
        b |= message.isRetainFlag() ? 0b1 : 0;
        AbstractMessage.QOSType qosType = message.getQos();
        if (qosType != null) {
            b |= (message.getQos().getValue() & 0b11) << 1;
        }
        b |= message.isDupFlag() ? 0b1000 : 0;
        return (byte)b;
    }

    protected static void applyByte1(AbstractMessage message, byte byte1)
    {
        message.setRetainFlag((byte1 & 0b1) != 0);

        AbstractMessage.QOSType qos = AbstractMessage.QOSType.MOST_ONE;
        switch ((byte1 & 0b110) >> 1)
        {
            case 1:
                qos = AbstractMessage.QOSType.LEAST_ONE;
                break;
            case 2:
                qos = AbstractMessage.QOSType.EXACTLY_ONCE;
                break;
            case 3:
                qos = AbstractMessage.QOSType.RESERVED;
                break;
        }
        message.setQos(qos);

        message.setDupFlag((byte1 & 0b1000) != 0);
    }
}
