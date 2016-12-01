package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.UnsubAckMessage;

/**
 * Created by ben on 8/26/14.
 */
public class EncodeUnsuback {
    public static void encode(UnsubAckMessage message, ByteBuf buffer)
    {
        buffer.writeByte(Helper.block1(message)).writeByte((byte) 2);
        buffer.writeShort(message.getMessageID());
    }
}
