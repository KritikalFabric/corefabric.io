package org.kritikal.fabric.net.mqtt;

import org.kritikal.fabric.net.mqtt.entities.ConnectMessage;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by ben on 8/25/14.
 */
public interface IMqttServerCallback {
    public void connected(MqttServerProtocol protocol, ConnectMessage connectMessage);
    public void postConnAck(MqttServerProtocol protocol, ConnectMessage connectMessage);
    public void messageArrived(MqttServerProtocol protocol, PublishMessage publishMessage);
    public boolean authorize(String username, byte[] password);
    public byte subscribe(MqttServerProtocol protocol, MqttSubscription subscription);
    public void publishRetained(MqttServerProtocol protocol, ConcurrentLinkedQueue<MqttSubscription> newSubscriptions);
    public void unsubscribe(MqttServerProtocol protocol, String topic);
    public void disconnected(MqttServerProtocol protocol);
}
