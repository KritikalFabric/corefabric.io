package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.entities.AbstractMessage;
import org.kritikal.fabric.net.mqtt.entities.SubAckMessage;

/**
 * Created by ben on 8/26/14.
 */
public class EncodeSuback {
    public static void encode(SubAckMessage message, ByteBuf buffer)
    {
        ByteBuf block2 = null;
        ByteBuf block3 = null;
        try {
            block3 = Unpooled.buffer();
            block3.writeShort(message.getMessageID());
            for (AbstractMessage.QOSType qosType : message.types())
                block3.writeByte(qosType.getValue());
            block2 = Helper.encodeRemainingLength(block3.readableBytes());
            buffer.writeByte(Helper.block1(message));
            buffer.writeBytes(block2);
            buffer.writeBytes(block3);
        }
        finally {
            if (block2 != null) block2.release();
            if (block3 != null) block3.release();
        }
    }
}
