package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.ConnAckMessage;

/**
 * Created by ben on 8/25/14.
 */
public class EncodeConnack {
    public static void encode(ConnAckMessage message, ByteBuf byteBuf)
    {
        byteBuf.writeByte(0x20);
        byteBuf.writeByte(0x02);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(message.getReturnCode());
    }
}
