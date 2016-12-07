/**
 * fabric's mqtt interface with angularjs
 */

import { Injectable, NgZone } from '@angular/core';

import { Scheduler } from 'rxjs/Rx';

import { FabricHelpers } from './fabric.helpers.service';

import { UUID } from '../entities/uuid';

declare var Paho; // vendor/eclipse-paho

export interface FabricCommsOnMessageCallback {
    (o:any, t:any): any
}

export class MqttSubscription {
    constructor(public topicPattern:string) {
        this.parts = this.topicPattern.split(/\//);
    }
    private parts:string[];
    public matches(other:MqttSubscription):boolean
    {
        return this.matchesRecursive(0,0,other);
    }
    public matchesRecursive(i:number, j:number, mqttTopic:MqttSubscription):boolean
    {
        for (; i < this.parts.length && j < mqttTopic.parts.length; ++i, ++j)
        {
            if ("+" == this.parts[i]) {
                continue;
            }
            else if ("#" == this.parts[i]) {
                if (i == this.parts.length - 1)
                    return true;
                for (let x = i + 1; x < this.parts.length; ++x) {
                    for (let y = j; y < mqttTopic.parts.length; ++y) {
                        if (this.matchesRecursive(x, y, mqttTopic)) {
                            return true;
                        }
                    }
                }
            }
            else if (this.parts[i] != mqttTopic.parts[j]) {
                return false;
            }
        }
        return i == this.parts.length && j == mqttTopic.parts.length; // remember i or j just overflowed
    }
}

@Injectable()
export class FabricComms {
    constructor(private zone:NgZone, private fabricHelpers: FabricHelpers) {
        var cookie = fabricHelpers.getCookie('corefabric');
        if (cookie == null) {
            console.log('cookie missing!');
            cookie = fabricHelpers.generateRandomUUID();
        }
        this.clientID = 'corefabric--' + fabricHelpers.getCookie('corefabric') + '--' + this.uuid.getValue();
        this.mqttClient = new Paho.MQTT.Client(fabricHelpers.getHost(), Number(1080), this.clientID);
        this.connected = false;
        this.activeSubscriptions = [];
        this.firstConnect = true;
        this.reconnectSubscriptions = [];
        this.reconnectUnsubscriptions = [];
        this.onConnect = [];
        this.onMessage = [];
        var comms:FabricComms = this;
        this.mqttClient.onConnectionLost = function(responseObject) {
            comms.zone.run(function(){
                console.log('MQTT connection lost: ' + responseObject.errorMessage);
                comms.connected = false;
                for (let i = 0, l = comms.onConnect.length; i < l; ++i) {
                    try {
                        (comms.onConnect[i])(comms.connected);
                    }
                    catch (err) { }
                }
            });
        };
        this.mqttClient.onMessageArrived = function(message) {
            comms.zone.run(function(){
                //console.log("MQTT message arrived:" + JSON.stringify(message));
                for (let i = 0, l = comms.onMessage.length; i < l; ++i) {
                    try {
                        (comms.onMessage[i])(message);
                    }
                    catch (err) { }
                }
                let destination = new MqttSubscription(message.destinationName);
                for (var i = 0; i < comms.activeSubscriptions.length; ++i) {
                    var s = comms.activeSubscriptions[i];
                    let subscription = new MqttSubscription(s[0]);
                    if (subscription.matches(destination)) {
                        if (s[2]) {
                            try {
                                var x = comms.fabricHelpers.utf8Parse(message.payloadBytes);
                                try {
                                    console.log('text:'+message.destinationName+':'+x);
                                    (s[1])(x, message.destinationName);
                                }
                                catch (e3) {
                                    console.log("onMessageArrived callback " + e3);
                                }
                            }
                            catch (e1) {
                                console.log("onMessageArrived utf8 parse " + e1);
                            }
                        } else {
                            try {
                                var json = comms.fabricHelpers.utf8Parse(message.payloadBytes);
                                try {
                                    var data = JSON.parse(json);
                                    try {
                                        console.log('json:'+message.destinationName+':'+json);
                                        (s[1])(data, message.destinationName);
                                    }
                                    catch (e3) {
                                        console.log("onMessageArrived callback " + e3);
                                    }
                                }
                                catch (e2) {
                                    console.log("onMessageArrived json parse " + e2);
                                }
                            }
                            catch (e1) {
                                console.log("onMessageArrived utf8 parse " + e1);
                            }
                        }
                    }
                }
            });
        };
        // finally
        this.connect();
        var work:any = function() {
            comms.connect();
            Scheduler.async.schedule(work, 15000, null);
        };
        Scheduler.async.schedule(work, 15000, null);
    }

    private uuid:UUID = new UUID(null);
    private clientID:string;
    private mqttClient:any;
    private connected:boolean;
    private activeSubscriptions:any[];
    private firstConnect:boolean;
    private reconnectSubscriptions:any[];
    private reconnectUnsubscriptions:any[];

    private onConnect:any[];
    private onMessage:any[];

    // public api

    register(onConnect:any, onMessage:any):void {
        if (onConnect !== null) this.onConnect.push(onConnect);
        if (onMessage !== null) this.onMessage.push(onMessage);
    }

    publish(topic: string, payload:any, qos:number, retain:boolean) {
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function(){
                mqttClient.send(topic, payload, qos, retain);
            });
        }
    }

    subscribe(topicPattern: string, callback: FabricCommsOnMessageCallback, notJson?:boolean) {
        this.activeSubscriptions.push([topicPattern, callback, !!notJson]);
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function(){
                mqttClient.subscribe(topicPattern);
            });
        } else {
            this.reconnectSubscriptions.push(topicPattern);
        }
    }

    //resubscribe(topicPattern: string, callback: FabricCommsOnMessageCallback) {
    //    for (var i = 0; i < this.activeSubscriptions.length; ++i) {
    //        if (this.activeSubscriptions[i][0] == topicPattern)
    //            this.activeSubscriptions[i][1] = callback;
    //    }
    //}

    unsubscribe(topicPattern: string) {
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function(){
                mqttClient.unsubscribe(topicPattern);
            });
        } else {
            this.reconnectUnsubscriptions.push(topicPattern);
        }
        var ary = this.activeSubscriptions;
        this.activeSubscriptions = [];
        for (var i = 0; i < ary.length; ++i) {
            if (ary[i][0] == topicPattern) continue;
            this.activeSubscriptions.push(ary[i]);
        }
    }

    unsubscribeAll() {
        if (this.connected) {
            for (var i = 0; i < this.activeSubscriptions.length; ++i) {
                var topicPattern = this.activeSubscriptions[i][0];
                var mqttClient = this.mqttClient;
                this.zone.runOutsideAngular(function(){
                    mqttClient.unsubscribe(topicPattern);
                });
            }
        }
        this.activeSubscriptions = [];
    }

    // private api

    private connect() {
        if (!this.connected) {
            var that:FabricComms = this;
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function(){
                mqttClient.connect({
                    keepAliveInterval: 15,
                    cleanSession: that.firstConnect,
                    onSuccess: function() {
                        that.zone.run(function(){
                            console.log('MQTT connected');
                            that.connected = true;
                            if (that.firstConnect) {
                                that.firstConnect = false;
                                for (var i = 0; i < that.activeSubscriptions.length; ++i) {
                                    var topicPattern = that.activeSubscriptions[i][0];
                                    that.zone.runOutsideAngular(function(){
                                        mqttClient.subscribe(topicPattern);
                                    });
                                }
                            }
                            if (that.reconnectSubscriptions.length > 0) {
                                var ary = that.reconnectSubscriptions;
                                that.reconnectSubscriptions = [];
                                for (var i = 0; i < ary.length; ++i) {
                                    var topicPattern = ary[i];
                                    that.zone.runOutsideAngular(function(){
                                        mqttClient.subscribe(topicPattern);
                                    });
                                }
                            }
                            if (that.reconnectUnsubscriptions.length > 0) {
                                var ary = that.reconnectUnsubscriptions;
                                that.reconnectUnsubscriptions = [];
                                for (var i = 0; i < ary.length; ++i) {
                                    var topicPattern = ary[i];
                                    that.zone.runOutsideAngular(function(){
                                        mqttClient.unsubscribe(topicPattern);
                                    });
                                }
                            }
                        });
                    }
                });
            });
        }
    }

}