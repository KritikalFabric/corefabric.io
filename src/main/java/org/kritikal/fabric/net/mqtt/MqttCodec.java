package org.kritikal.fabric.net.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kritikal.fabric.net.mqtt.codec.*;
import org.kritikal.fabric.net.BufferContainer;
import org.kritikal.fabric.net.mqtt.entities.*;
import io.vertx.core.buffer.Buffer;

/**
 * Created by ben on 8/24/14.
 */
public class MqttCodec {

    public static Buffer encode(ConnectMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeConnect.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(ConnAckMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeConnack.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PingReqMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePingreq.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PingRespMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePingresp.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(SubscribeMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeSubscribe.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(SubAckMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeSuback.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PubAckMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePuback.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PubRecMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePubrec.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PubCompMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePubcomp.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(DisconnectMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeDisconnect.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PublishMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePublish.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(PubRelMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodePubrel.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(UnsubscribeMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeUnsubscribe.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static Buffer encode(UnsubAckMessage message)
    {
        Buffer buffer = Buffer.buffer();
        ByteBuf byteBuf = Unpooled.buffer();
        EncodeUnsuback.encode(message, byteBuf);
        buffer.setBytes(0, byteBuf.array(), 0, byteBuf.readableBytes());
        byteBuf.release();
        return buffer;
    }

    public static AbstractMessage decode(BufferContainer bufferContainer, byte messageType)
            throws BufferContainer.NeedMoreDataException, Exception
    {
        switch (messageType)
        {
            case AbstractMessage.CONNECT:
                return DecodeConnect.decode(bufferContainer);

            case AbstractMessage.CONNACK:
                return DecodeConnack.decode(bufferContainer);

            case AbstractMessage.PINGREQ:
                return DecodePingreq.decode(bufferContainer);

            case AbstractMessage.PINGRESP:
                return DecodePingresp.decode(bufferContainer);

            case AbstractMessage.SUBSCRIBE:
                return DecodeSubscribe.decode(bufferContainer);

            case AbstractMessage.SUBACK:
                return DecodeSuback.decode(bufferContainer);

            case AbstractMessage.UNSUBSCRIBE:
                return DecodeUnsubscribe.decode(bufferContainer);

            case AbstractMessage.UNSUBACK:
                return DecodeUnsuback.decode(bufferContainer);

            case AbstractMessage.PUBLISH:
                return DecodePublish.decode(bufferContainer);

            case AbstractMessage.PUBREL:
                return DecodePubrel.decode(bufferContainer);

            case AbstractMessage.PUBACK:
                return DecodePuback.decode(bufferContainer);

            case AbstractMessage.PUBREC:
                return DecodePubrec.decode(bufferContainer);

            case AbstractMessage.PUBCOMP:
                return DecodePubcomp.decode(bufferContainer);
        }

        throw new Exception();
    }
}
