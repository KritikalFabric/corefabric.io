"use strict";
/**
 * Helper methods that don't live elsewhere, e.g. where did ngCookies go?
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
var http_1 = require("@angular/http");
var FabricHelpers = /** @class */ (function () {
    function FabricHelpers(http) {
        this.http = http;
    }
    FabricHelpers.prototype.getHost = function () {
        var s = __window.location.host;
        s = s.replace(/:\d+$/, '');
        return s;
    };
    FabricHelpers.prototype.getDevicePixelRatio = function () {
        if (__window.devicePixelRatio) {
            return __window.devicePixelRatio;
        }
        return 1.0;
    };
    FabricHelpers.prototype.getCookie = function (name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ')
                c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) == 0)
                return c.substring(nameEQ.length, c.length);
        }
        return null;
    };
    FabricHelpers.prototype.generateRandomUUID = function () {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    };
    FabricHelpers.prototype.utf8Parse = function (input) {
        return (new TextDecoder('utf-8')).decode(input);
    };
    FabricHelpers = __decorate([
        core_1.Injectable(),
        __metadata("design:paramtypes", [http_1.Http])
    ], FabricHelpers);
    return FabricHelpers;
}());
exports.FabricHelpers = FabricHelpers;
//# sourceMappingURL=fabric.helpers.service.js.map