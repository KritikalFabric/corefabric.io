package org.kritikal.fabric.net.mqtt.entities;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by ben on 11/29/16.
 */
public class PublishMessageStreamSerializer implements StreamSerializer<PublishMessage> {

    @Override
    public int getTypeId() {
        return 1; // unique
    }

    @Override
    public void write(ObjectDataOutput out, PublishMessage publishMessage) throws IOException {
        out.writeUTF(publishMessage.getTopicName());
        out.writeByteArray(publishMessage.getPayload().array());
        out.writeByte(publishMessage.getQos().getValue());
        out.writeLong(publishMessage.expires);
        out.writeUTF(publishMessage.origin);
    }

    @Override
    public PublishMessage read(ObjectDataInput in) throws IOException {
        PublishMessage publishMessage = new PublishMessage();
        publishMessage.setTopicName(in.readUTF());
        publishMessage.setPayload(ByteBuffer.wrap(in.readByteArray()));
        byte qos = in.readByte();
        AbstractMessage.QOSType qosType = qos == 0 ? AbstractMessage.QOSType.MOST_ONE : (qos <= 1 ? AbstractMessage.QOSType.LEAST_ONE : (qos == 2 ? AbstractMessage.QOSType.EXACTLY_ONCE : AbstractMessage.QOSType.RESERVED));
        publishMessage.setQos(qosType);
        publishMessage.expires = in.readLong();
        publishMessage.origin = in.readUTF();
        return publishMessage;
    }

    @Override
    public void destroy() {}

}
