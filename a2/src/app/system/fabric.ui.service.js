"use strict";
/**
 * common ui handling code
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
var fabric_comms_service_1 = require("./fabric.comms.service");
var FabricUIService = /** @class */ (function () {
    function FabricUIService(fabricComms) {
        this.fabricComms = fabricComms;
        this.lastErrcode = '';
        this.lastErr = '';
        this.sidebar = false; // closed by default
        var service = this;
        fabricComms.register(function () {
            service.showError("disconnected", null);
        }, function (message) {
            service.clearError();
        });
    }
    FabricUIService.prototype.showError = function (errcode, err) {
        console.log('SEVERE -- ' + errcode + ' [' + err + ']');
        this.lastErrcode = errcode;
        this.lastErr = err;
        return this.getError(errcode);
    };
    FabricUIService.prototype.clearError = function () {
        this.lastErrcode = '';
        this.lastErr = '';
    };
    FabricUIService.prototype.getError = function (errcode) {
        switch (errcode) {
            case '':
                return '';
            case 'disconnected':
                return "reconnecting...";
            case 'FABRIC-UI-00001':
                return "code 1";
            case 'FABRIC-UI-00002':
                return "code 2";
            case 'FABRIC-API-00003':
                return "code 3";
        }
        return "don't panic";
    };
    FabricUIService.prototype.hasLastError = function () {
        return this.lastErrcode != '';
    };
    FabricUIService.prototype.getLastError = function () {
        return this.getError(this.lastErrcode);
    };
    FabricUIService.prototype.getLastErrorDetail = function () {
        return this.lastErr;
    };
    FabricUIService.prototype.toggleSidebar = function () {
        this.sidebar = !this.sidebar;
    };
    FabricUIService.prototype.getIsSidebarOpen = function () { return this.sidebar; };
    FabricUIService.prototype.setIsSidebarOpen = function (v) { this.sidebar = v; };
    FabricUIService = __decorate([
        core_1.Injectable(),
        __metadata("design:paramtypes", [fabric_comms_service_1.FabricComms])
    ], FabricUIService);
    return FabricUIService;
}());
exports.FabricUIService = FabricUIService;
//# sourceMappingURL=fabric.ui.service.js.map