package org.kritikal.fabric.net.mqtt.codec;

import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

import java.io.UnsupportedEncodingException;
import java.nio.*;
import java.util.UUID;

/**
 * Created by ben on 8/25/14.
 */
public class DecodePublish {
    public static PublishMessage decode(BufferContainer bufferContainer)
            throws BufferContainer.NeedMoreDataException,
            UnsupportedEncodingException,
            Exception
    {
        return decode(bufferContainer, false);
    }
    public static PublishMessage decode(BufferContainer bufferContainer, boolean decodeExtraFields)
            throws BufferContainer.NeedMoreDataException,
            UnsupportedEncodingException,
            Exception {
        byte byte1 = bufferContainer.readByte();
        long remainingLength = bufferContainer.readRemainingLength();
        bufferContainer.assertBytes(remainingLength);
        PublishMessage message = new PublishMessage();
        Helper.applyByte1(message, byte1);
        int topicLength = bufferContainer.readShort();
        if (topicLength <= 0) throw new Exception("Topic length " + topicLength);
        byte[] topicUtf8 = bufferContainer.readBytes(topicLength);
        long payloadLength = remainingLength - 2 - topicLength;
        if (message.getQos().getValue() == 1 ||
            message.getQos().getValue() == 2) {
            int messageID = bufferContainer.readShort();
            message.setMessageID(messageID);
            payloadLength -= 2;
        }
        if (topicUtf8 != null)
            message.setTopicName(new String(topicUtf8, "UTF-8"));
        ByteBuffer payloadBuffer = payloadLength > 0
                ? ByteBuffer.wrap(bufferContainer.readBytes(payloadLength))
                : null;
        message.setPayload(payloadBuffer);
        if (decodeExtraFields) {
            message.expires = bufferContainer.readLong();
            message.origin = new UUID(bufferContainer.readLong(), bufferContainer.readLong());
        }
        return message;
    }
}
