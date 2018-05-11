"use strict";
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
var http_1 = require("@angular/http");
var fabric_comms_service_1 = require("./fabric.comms.service");
var fabric_helpers_service_1 = require("./fabric.helpers.service");
var fabric_ui_service_1 = require("./fabric.ui.service");
var FabricDocApiParameters = /** @class */ (function () {
    function FabricDocApiParameters() {
    }
    return FabricDocApiParameters;
}());
var FabricApiStatus = /** @class */ (function () {
    function FabricApiStatus() {
        this.complete = false;
        this.slowQuery = false;
    }
    FabricApiStatus.prototype.onUpdate = function (data, topic) {
        this.data = data;
        // TODO: update status visually somehow
    };
    FabricApiStatus.prototype.onComplete = function () {
        this.complete = true;
    };
    return FabricApiStatus;
}());
exports.FabricApiStatus = FabricApiStatus;
var FabricDocApiStatus = /** @class */ (function () {
    function FabricDocApiStatus() {
        this.complete = false;
    }
    FabricDocApiStatus.prototype.onUpdate = function (data) {
        this.data = data;
        // TODO: update status visually somehow
    };
    FabricDocApiStatus.prototype.onComplete = function () {
        this.complete = true;
    };
    return FabricDocApiStatus;
}());
exports.FabricDocApiStatus = FabricDocApiStatus;
var FabricDocApi = /** @class */ (function () {
    function FabricDocApi(http, fabricComms, fabricHelpers, fabricUI) {
        this.http = http;
        this.fabricComms = fabricComms;
        this.fabricHelpers = fabricHelpers;
        this.fabricUI = fabricUI;
    }
    // TODO: wire up with the UI service (notifications etc)
    FabricDocApi.prototype.open_singleton = function (ad, onDoc) {
        return this.remote(ad, 'open_singleton', null, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(ad);
        });
    };
    FabricDocApi.prototype.open = function (ad, onDoc) {
        return this.remote(ad, 'open', null, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(ad);
        });
    };
    FabricDocApi.prototype.list = function (ad, onDoc) {
        return this.remote(ad, 'list', null, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(data);
        });
    };
    FabricDocApi.prototype.upsert = function (ad, onDoc) {
        return this.remote(ad, 'upsert', null, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(ad);
        });
    };
    FabricDocApi.prototype.delete = function (ad, onDoc) {
        return this.remote(ad, 'delete', null, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(ad);
        });
    };
    FabricDocApi.prototype.call = function (ad, method, args, onDoc) {
        return this.remote(ad, method, args, function (data) {
            ad.data = Object.assign(ad.data, data);
            onDoc(ad);
        });
    };
    FabricDocApi.prototype.remote = function (ad, method, args, callback) {
        this.destroy(ad);
        var params = new FabricDocApiParameters();
        params.path = ad.getPath();
        params.method = method;
        params.args = args;
        params.ad = ad;
        var api = this;
        var headers = new http_1.Headers();
        headers.append('X-Preflight', '1');
        var options = new http_1.RequestOptions({
            method: http_1.RequestMethod.Post,
            url: 'http://' + this.fabricHelpers.getHost() + ':1080/api/doc',
            headers: headers
        });
        console.log("docapi " + method);
        //console.log("calling " + JSON.stringify(params));
        this.http
            .post(options.url, JSON.stringify(params), options)
            .subscribe(function (response) {
            var data = response.json();
            if (data.success) {
                var succeeded = false;
                var statusListener = new FabricDocApiStatus();
                statusListener.params = params;
                statusListener.topic = data.topic;
                ad.mqttTopic = data.topic;
                statusListener.statusTopic = data.statusTopic;
                statusListener.data = null;
                api.fabricComms.subscribe(statusListener.statusTopic, statusListener.onUpdate);
                api.fabricComms.subscribe(ad.mqttTopic, function (data, topic) {
                    succeeded = true;
                    var ary = ad.statusListeners;
                    ad.statusListeners = [];
                    for (var i = 0; i < ary.length; ++i) {
                        if (ary[i].statusTopic != statusListener.statusTopic)
                            ad.statusListeners.push(ary[i]);
                    }
                    api.fabricComms.unsubscribe(statusListener.statusTopic);
                    try {
                        //console.log(JSON.stringify(data));
                        callback(data, topic);
                    }
                    catch (e) {
                        api.fabricUI.showError('FABRIC-API-00003', e);
                    }
                });
            }
            else {
                api.fabricUI.showError(data.error, JSON.stringify(params));
            }
        }, function () {
            console.log('HTTP error for ' + JSON.stringify(params));
        });
        return ad;
    };
    FabricDocApi.prototype.destroy = function (ad) {
        if (ad == null)
            return;
        if (ad.mqttTopic) {
            this.fabricComms.unsubscribe(ad.mqttTopic);
            ad.mqttTopic = null;
        }
        if (ad.statusListeners) {
            for (var _i = 0, _a = ad.statusListeners; _i < _a.length; _i++) {
                var sl = _a[_i];
                this.fabricComms.unsubscribe(sl.topic);
                this.fabricComms.unsubscribe(sl.statusTopic);
            }
        }
        ad.statusListeners = [];
    };
    FabricDocApi = __decorate([
        core_1.Injectable(),
        __metadata("design:paramtypes", [http_1.Http, fabric_comms_service_1.FabricComms, fabric_helpers_service_1.FabricHelpers, fabric_ui_service_1.FabricUIService])
    ], FabricDocApi);
    return FabricDocApi;
}());
exports.FabricDocApi = FabricDocApi;
//# sourceMappingURL=fabric.docapi.service.js.map