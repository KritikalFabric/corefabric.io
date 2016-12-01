package org.kritikal.fabric.net.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Created by ben on 8/25/14.
 */
public class EncodePublish {
    public static void encode(PublishMessage message, ByteBuf buffer) {
        encode(message, buffer, false);
    }
    public static void encode(PublishMessage message, ByteBuf buffer, boolean encodeExtraFields)
    {
        ByteBuf block2 = null;
        ByteBuf block3 = null;
        try {
            buffer.writeByte(Helper.block1(message));
            block3 = Unpooled.buffer();
            byte[] topicUtf8 = null;
            try
            {
                topicUtf8 = message.getTopicName().getBytes("UTF-8");
            }
            catch (UnsupportedEncodingException ex)
            {
                // ignore
            }
            block3.writeShort(topicUtf8 != null ? topicUtf8.length : 0);
            if (topicUtf8 != null) block3.writeBytes(topicUtf8);
            if (message.getQos().getValue() == 1 ||
                message.getQos().getValue() == 2) {
                block3.writeShort(message.getMessageID());
            }
            ByteBuffer payload = message.getPayload();
            if (payload != null) {
                block3.writeBytes(payload.array());
            }
            block2 = Helper.encodeRemainingLength(block3.readableBytes());
            buffer.writeBytes(block2).writeBytes(block3);

            if (encodeExtraFields) {
                buffer.writeLong(message.expires);
                buffer.writeLong(message.origin.getMostSignificantBits());
                buffer.writeLong(message.origin.getLeastSignificantBits());
            }
        }
        finally {
            if (block3 != null) block3.release();
            if (block2 != null) block2.release();
        }
    }
}
