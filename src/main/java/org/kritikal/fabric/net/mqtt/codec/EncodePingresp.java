package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.PingRespMessage;

/**
 * Created by ben on 8/25/14.
 */
public class EncodePingresp {
    public static void encode(PingRespMessage message, ByteBuf buffer)
    {
        buffer.writeByte(Helper.block1(message)).writeByte((byte)0);
    }
}
