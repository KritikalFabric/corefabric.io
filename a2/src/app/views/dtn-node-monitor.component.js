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
var ui_service_1 = require("../local/ui.service");
var fabric_comms_service_1 = require("../system/fabric.comms.service");
var fabric_helpers_service_1 = require("../system/fabric.helpers.service");
var DtnNodeMonitorComponent = /** @class */ (function () {
    function DtnNodeMonitorComponent(ui, comms, http, fabricHelpers) {
        this.ui = ui;
        this.comms = comms;
        this.http = http;
        this.fabricHelpers = fabricHelpers;
        this.state = {
            topic: '',
            newMessage: '',
            system: false
        };
        this.messages = [];
        this.ui.setTitle('node monitor', 'node-monitor', '(on-node) MQTT monitor');
        var x = this;
        this.http.get('http://' + this.fabricHelpers.getHost() + ':1080/api/json/node-monitor').map(function (res) { return res.json(); }).toPromise().then(function (o) { x.state.topic = o.topic; });
    }
    DtnNodeMonitorComponent.prototype.ngOnInit = function () {
        var x = this;
        this.comms.subscribe('#', function (m, t) {
            console.log('got ' + m + ' at ' + t);
            if (!x.state.system && /^\$/.test(t))
                return;
            x.messages.push({ topic: t, message: m });
            while (x.messages.length > 10) {
                x.messages.shift();
            }
        }, true);
    };
    DtnNodeMonitorComponent.prototype.ngOnDestroy = function () {
        this.comms.unsubscribe('#');
    };
    DtnNodeMonitorComponent.prototype.publish = function () {
        if ('' == this.state.topic)
            return;
        var text = '' + this.state.newMessage;
        this.state.newMessage = '';
        this.comms.publish(this.state.topic, text, 2, false);
    };
    DtnNodeMonitorComponent = __decorate([
        core_1.Component({
            selector: 'dtn-node-monitor',
            templateUrl: 'app/views/dtn-node-monitor.component.html'
        }),
        __metadata("design:paramtypes", [ui_service_1.UIService, fabric_comms_service_1.FabricComms, http_1.Http, fabric_helpers_service_1.FabricHelpers])
    ], DtnNodeMonitorComponent);
    return DtnNodeMonitorComponent;
}());
exports.DtnNodeMonitorComponent = DtnNodeMonitorComponent;
//# sourceMappingURL=dtn-node-monitor.component.js.map