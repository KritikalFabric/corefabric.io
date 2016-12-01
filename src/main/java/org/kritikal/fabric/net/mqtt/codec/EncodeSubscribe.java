package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.entities.SubscribeMessage;

import java.io.UnsupportedEncodingException;

/**
 * Created by ben on 8/24/14.
 */
public class EncodeSubscribe {
    public static void encode(SubscribeMessage message, ByteBuf buffer)
    {
        ByteBuf block2 = null;
        ByteBuf block3 = null;
        try {
            block3 = Unpooled.buffer();
            block3.writeShort(message.getMessageID());
            for (SubscribeMessage.Couple couple : message.subscriptions()) {
                try {
                    byte[] raw = couple.getTopicFilter().getBytes("UTF-8");
                    block3.writeShort(raw.length);
                    block3.writeBytes(raw);
                    block3.writeByte(couple.getQos());
                }
                catch (UnsupportedEncodingException ex) {
                    // ignore, won't happen
                }
            }
            long remainingLength = block3.readableBytes();
            block2 = Helper.encodeRemainingLength(remainingLength);
            buffer.writeByte(Helper.block1(message)).writeBytes(block2).writeBytes(block3);
            return;
        }
        finally {
            if (block3 != null) block3.release();
            if (block2 != null) block2.release();
        }
    }
}
