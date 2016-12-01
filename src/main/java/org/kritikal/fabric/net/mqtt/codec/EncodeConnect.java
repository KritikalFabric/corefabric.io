package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.entities.ConnectMessage;

/**
 * Created by ben on 8/24/14.
 */
public class EncodeConnect {
    public static void encode(ConnectMessage message, ByteBuf buffer)
    {
        ByteBuf block2 = null;
        ByteBuf block3 = null;
        ByteBuf block4 = null;
        try {
            block3 = Unpooled.buffer();
            block3.writeBytes(Helper.encodeUtf8(message.getProtocolName()));
            block3.writeByte(message.getProtocolVersion());
            int connectFlags =
                      (message.isCleanSession() ? 0b10 : 0)
                    | (message.isWillFlag() ? 0xb100 : 0)
                    | (0b11000 & (message.getWillQos() << 3))
                    | (message.isWillRetain() ? 0b100000 : 0)
                    | (message.isPasswordFlag() ? 0b1000000 : 0)
                    | (message.isUserFlag() ? 0b10000000 : 0);
            block3.writeByte(connectFlags);
            block3.writeShort(message.getKeepAlive());

            block4 = Unpooled.buffer();
            block4.writeBytes(Helper.encodeUtf8(message.getClientID()));
            if (message.isWillFlag())
                block4.writeBytes(Helper.encodeUtf8(message.getWillTopic()));
            if (message.isWillFlag())
                block4.writeBytes(message.getWillMessage());
            if (message.isUserFlag())
                block4.writeBytes(Helper.encodeUtf8(message.getUsername()));
            if (message.isPasswordFlag())
                block4.writeBytes(message.getPassword());

            long remainingLength = block3.readableBytes() + block4.readableBytes();
            block2 = Helper.encodeRemainingLength(remainingLength);

            // all prepared now write & return

            buffer.writeByte(Helper.block1(message)).writeBytes(block2).writeBytes(block3).writeBytes(block4);

            return;
        }
        finally {
            if (block4 != null) block4.release();
            if (block3 != null) block3.release();
            if (block2 != null) block2.release();
        }
    }
}
