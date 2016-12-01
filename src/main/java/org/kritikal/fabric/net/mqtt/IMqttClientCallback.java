package org.kritikal.fabric.net.mqtt;

import org.kritikal.fabric.net.mqtt.entities.ConnAckMessage;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;

/**
 * Created by ben on 8/25/14.
 */
public interface IMqttClientCallback {
    public void connectSuccessful();
    public void connectError(ConnAckMessage message);
    public void messageArrived(PublishMessage publishMessage);
    public void disconnected(boolean cleanDisconnect);
}
