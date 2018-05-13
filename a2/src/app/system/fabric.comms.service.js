"use strict";
/**
 * fabric's mqtt interface with angularjs
 */
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = require("@angular/core");
var Rx_1 = require("rxjs/Rx");
var fabric_helpers_service_1 = require("./fabric.helpers.service");
var uuid_1 = require("../entities/uuid");
var MqttSubscription = /** @class */ (function () {
    function MqttSubscription(topicPattern) {
        this.topicPattern = topicPattern;
        this.parts = this.topicPattern.split(/\//);
    }
    MqttSubscription.prototype.matches = function (other) {
        return this.matchesRecursive(0, 0, other);
    };
    MqttSubscription.prototype.matchesRecursive = function (i, j, mqttTopic) {
        for (; i < this.parts.length && j < mqttTopic.parts.length; ++i, ++j) {
            if ("+" == this.parts[i]) {
                continue;
            }
            else if ("#" == this.parts[i]) {
                if (i == this.parts.length - 1)
                    return true;
                for (var x = i + 1; x < this.parts.length; ++x) {
                    for (var y = j; y < mqttTopic.parts.length; ++y) {
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
    };
    return MqttSubscription;
}());
exports.MqttSubscription = MqttSubscription;
var FabricComms = /** @class */ (function () {
    function FabricComms(zone, fabricHelpers) {
        this.zone = zone;
        this.fabricHelpers = fabricHelpers;
        this.uuid = new uuid_1.UUID(null);
        var cookie = fabricHelpers.getCookie('corefabric');
        if (cookie == null) {
            console.log('cookie missing!');
            cookie = fabricHelpers.generateRandomUUID();
        }
        this.clientID = 'corefabric--' + fabricHelpers.getCookie('corefabric') + '--' + this.uuid.getValue();
        this.mqttClient = new Paho.MQTT.Client(fabricHelpers.getHost(), Number(1080), this.clientID);
        this.connected = false;
        this.activeSubscriptions = [];
        this.reconnectSubscriptions = [];
        this.reconnectUnsubscriptions = [];
        this.onConnect = [];
        this.onMessage = [];
        var comms = this;
        this.mqttClient.onConnectionLost = function (responseObject) {
            comms.zone.run(function () {
                console.log('MQTT connection lost: ' + responseObject.errorMessage);
                comms.connected = false;
                for (var i = 0, l = comms.onConnect.length; i < l; ++i) {
                    try {
                        (comms.onConnect[i])(comms.connected);
                    }
                    catch (err) { }
                }
                comms.connect();
            });
        };
        this.mqttClient.onMessageArrived = function (message) {
            comms.zone.run(function () {
                //console.log("MQTT message arrived:" + JSON.stringify(message));
                for (var i_1 = 0, l = comms.onMessage.length; i_1 < l; ++i_1) {
                    try {
                        (comms.onMessage[i_1])(message);
                    }
                    catch (err) { }
                }
                var destination = new MqttSubscription(message.destinationName);
                for (var i = 0; i < comms.activeSubscriptions.length; ++i) {
                    var s = comms.activeSubscriptions[i];
                    var subscription = new MqttSubscription(s[0]);
                    if (subscription.matches(destination)) {
                        if (s[2]) {
                            try {
                                var x = comms.fabricHelpers.utf8Parse(message.payloadBytes);
                                try {
                                    console.log('text:' + message.destinationName + ':' + x);
                                    (s[1])(x, message.destinationName);
                                }
                                catch (e3) {
                                    console.log("onMessageArrived callback " + e3);
                                }
                            }
                            catch (e1) {
                                console.log("onMessageArrived utf8 parse " + e1);
                            }
                        }
                        else {
                            try {
                                var json = comms.fabricHelpers.utf8Parse(message.payloadBytes);
                                try {
                                    var data = JSON.parse(json);
                                    try {
                                        console.log('json:' + message.destinationName + ':' + json);
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
        var work = function () {
            comms.connect();
            Rx_1.Scheduler.async.schedule(work, 15000, null);
        };
        Rx_1.Scheduler.async.schedule(work, 15000, null);
    }
    // public api
    FabricComms.prototype.register = function (onConnect, onMessage) {
        if (onConnect !== null)
            this.onConnect.push(onConnect);
        if (onMessage !== null)
            this.onMessage.push(onMessage);
    };
    FabricComms.prototype.publish = function (topic, payload, qos, retain) {
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function () {
                mqttClient.send(topic, payload, qos, retain);
            });
        }
    };
    FabricComms.prototype.subscribe = function (topicPattern, callback, notJson) {
        this.activeSubscriptions.push([topicPattern, callback, !!notJson]);
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function () {
                mqttClient.subscribe(topicPattern);
            });
        }
        else {
            this.reconnectSubscriptions.push(topicPattern);
        }
    };
    //resubscribe(topicPattern: string, callback: FabricCommsOnMessageCallback) {
    //    for (var i = 0; i < this.activeSubscriptions.length; ++i) {
    //        if (this.activeSubscriptions[i][0] == topicPattern)
    //            this.activeSubscriptions[i][1] = callback;
    //    }
    //}
    FabricComms.prototype.unsubscribe = function (topicPattern) {
        if (this.connected) {
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function () {
                mqttClient.unsubscribe(topicPattern);
            });
        }
        else {
            this.reconnectUnsubscriptions.push(topicPattern);
        }
        var ary = this.activeSubscriptions;
        this.activeSubscriptions = [];
        for (var i = 0; i < ary.length; ++i) {
            if (ary[i][0] == topicPattern)
                continue;
            this.activeSubscriptions.push(ary[i]);
        }
    };
    FabricComms.prototype.unsubscribeAll = function () {
        if (this.connected) {
            for (var i = 0; i < this.activeSubscriptions.length; ++i) {
                var topicPattern = this.activeSubscriptions[i][0];
                var mqttClient = this.mqttClient;
                this.zone.runOutsideAngular(function () {
                    mqttClient.unsubscribe(topicPattern);
                });
            }
        }
        this.activeSubscriptions = [];
    };
    // private api
    FabricComms.prototype.connect = function () {
        if (!this.connected) {
            var that = this;
            var mqttClient = this.mqttClient;
            this.zone.runOutsideAngular(function () {
                mqttClient.connect({
                    keepAliveInterval: 15,
                    cleanSession: true,
                    onSuccess: function () {
                        that.zone.run(function () {
                            console.log('MQTT connected');
                            that.connected = true;
                            {
                                for (var i = 0; i < that.activeSubscriptions.length; ++i) {
                                    var topicPattern = that.activeSubscriptions[i][0];
                                    that.zone.runOutsideAngular(function () {
                                        mqttClient.subscribe(topicPattern);
                                    });
                                }
                            }
                            if (that.reconnectSubscriptions.length > 0) {
                                var ary = that.reconnectSubscriptions;
                                that.reconnectSubscriptions = [];
                                for (var i = 0; i < ary.length; ++i) {
                                    var topicPattern = ary[i];
                                    that.zone.runOutsideAngular(function () {
                                        mqttClient.subscribe(topicPattern);
                                    });
                                }
                            }
                            if (that.reconnectUnsubscriptions.length > 0) {
                                var ary = that.reconnectUnsubscriptions;
                                that.reconnectUnsubscriptions = [];
                                for (var i = 0; i < ary.length; ++i) {
                                    var topicPattern = ary[i];
                                    that.zone.runOutsideAngular(function () {
                                        mqttClient.unsubscribe(topicPattern);
                                    });
                                }
                            }
                        });
                    }
                });
            });
        }
    };
    FabricComms = __decorate([
        core_1.Injectable(),
        __metadata("design:paramtypes", [core_1.NgZone, fabric_helpers_service_1.FabricHelpers])
    ], FabricComms);
    return FabricComms;
}());
exports.FabricComms = FabricComms;
//# sourceMappingURL=fabric.comms.service.js.map