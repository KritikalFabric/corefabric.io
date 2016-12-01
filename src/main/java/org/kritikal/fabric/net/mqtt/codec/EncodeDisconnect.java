package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import org.kritikal.fabric.net.mqtt.entities.DisconnectMessage;

/**
 * Created by ben on 8/25/14.
 */
public class EncodeDisconnect {
    public static void encode(DisconnectMessage message, ByteBuf buffer)
    {
        buffer.writeByte(Helper.block1(message));
        buffer.writeByte((byte)0);
    }
}
