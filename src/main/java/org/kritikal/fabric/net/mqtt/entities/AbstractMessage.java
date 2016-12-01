package org.kritikal.fabric.net.mqtt.entities;

import java.nio.ByteBuffer;

/**
 * Created by ben on 02/02/2016.
 */
public abstract class AbstractMessage {

    protected AbstractMessage(int messageType) {
        this.messageType = messageType;
    }

    public enum QOSType {
        MOST_ONE(0),
        LEAST_ONE(1),
        EXACTLY_ONCE(2),
        RESERVED(3);
        private QOSType(int value) { this.value = value; }
        private int value;
        public byte getValue() { return (byte)value; }
    };

    // see: http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html
    public static final int CONNECT     = 0b0001;
    public static final int CONNACK     = 0b0010;
    public static final int PUBLISH     = 0b0011;
    public static final int PUBACK      = 0b0100;
    public static final int PUBREC      = 0b0101;
    public static final int PUBREL      = 0b0110;
    public static final int PUBCOMP     = 0b0111;
    public static final int SUBSCRIBE   = 0b1000;
    public static final int SUBACK      = 0b1001;
    public static final int UNSUBSCRIBE = 0b1010;
    public static final int UNSUBACK    = 0b1011;
    public static final int PINGREQ     = 0b1100;
    public static final int PINGRESP    = 0b1101;
    public static final int DISCONNECT  = 0b1110;

    QOSType qos = QOSType.MOST_ONE;
    boolean retainFlag = false;
    boolean dupFlag = false;
    byte returnCode = 0;
    int messageType = 0;
    int messageID = 0;
    ByteBuffer payload = null;

    public int getMessageID() {
        return messageID;
    }

    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public void setPayload(ByteBuffer payload) {
        this.payload = payload;
    }

    public boolean isRetainFlag() {
        return retainFlag;
    }

    public boolean isDupFlag() {
        return dupFlag;
    }

    public void setDupFlag(boolean newValue) {
        dupFlag = newValue;
    }

    public boolean getDupFlag() {
        return dupFlag;
    }

    public void setRetainFlag(boolean newValue) {
        retainFlag = newValue;
    }

    public void setReturnCode(byte newValue) {
        returnCode = newValue;
    }

    public byte getReturnCode() {
        return returnCode;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int newValue) {
        messageType = newValue;
    }

    public QOSType getQos() {
        return qos;
    }

    public void setQos(QOSType newValue) {
        qos = newValue;
    }

};
