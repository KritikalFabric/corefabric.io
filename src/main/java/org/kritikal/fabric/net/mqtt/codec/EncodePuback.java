package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.PubAckMessage;

/**
 * Created by ben on 8/25/14.
 */
public class EncodePuback {
    public static void encode(PubAckMessage message, ByteBuf buffer)
    {
        buffer.writeByte(Helper.block1(message)).writeByte((byte)2);
        buffer.writeShort(message.getMessageID());
    }
}
