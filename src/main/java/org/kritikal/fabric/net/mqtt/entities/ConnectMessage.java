package org.kritikal.fabric.net.mqtt.entities;

/**
 * Created by ben on 02/02/2016.
 */
public class ConnectMessage extends AbstractMessage {

    public ConnectMessage() {
        super(AbstractMessage.CONNECT);
    }

    String protocolName = "MQTT";
    byte protocolVersion = 4;
    boolean willFlag = false;
    byte willQos = 0;
    boolean willRetain = false;
    boolean passwordFlag = false;
    boolean userFlag = false;
    String userName = null;
    byte[] password = null;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getWillTopic() {
        return willTopic;
    }

    public void setWillTopic(String willTopic) {
        this.willTopic = willTopic;
    }

    public byte[] getWillMessage() {
        return willMessage;
    }

    public void setWillMessage(byte[] willMessage) {
        this.willMessage = willMessage;
    }

    public int getKeepAlive() {
        return keepAliveTimer;
    }

    public void setKeepAlive(int keepAliveTimer) {
        this.keepAliveTimer = keepAliveTimer;
    }

    String clientID = null;
    String willTopic = null;
    byte[] willMessage = null;
    int keepAliveTimer = 0;

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    boolean cleanSession = false;

    public boolean isWillFlag() {
        return willFlag;
    }

    public void setWillFlag(boolean willFlag) {
        this.willFlag = willFlag;
    }

    public byte getWillQos() {
        return willQos;
    }

    public void setWillQos(byte willQos) {
        this.willQos = willQos;
    }

    public boolean isWillRetain() {
        return willRetain;
    }

    public void setWillRetain(boolean willRetain) {
        this.willRetain = willRetain;
    }

    public boolean isPasswordFlag() {
        return passwordFlag;
    }

    public void setPasswordFlag(boolean passwordFlag) {
        this.passwordFlag = passwordFlag;
    }

    public boolean isUserFlag() {
        return userFlag;
    }

    public void setUserFlag(boolean userFlag) {
        this.userFlag = userFlag;
    }

    public String getUsername() {
        return userName;
    }

    public void setUsername(String userName) {
        this.userName = userName;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public void setProtocolName(String newValue) {
        protocolName = newValue;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte newValue) {
        protocolVersion = newValue;
    }
}
