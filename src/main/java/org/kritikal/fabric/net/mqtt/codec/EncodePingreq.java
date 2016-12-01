package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.PingReqMessage;

/**
 * Created by ben on 8/24/14.
 */
public class EncodePingreq {
    public static void encode(PingReqMessage message, ByteBuf buffer)
    {
        buffer.writeByte(Helper.block1(message)).writeByte((byte)0);
    }
}
