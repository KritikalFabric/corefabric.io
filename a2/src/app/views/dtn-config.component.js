"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
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
var fabric_docapi_service_1 = require("../system/fabric.docapi.service");
var fabric_apipage_entity_1 = require("../entities/fabric.apipage.entity");
var fabric_ui_entity_1 = require("../entities/fabric.ui.entity");
var fabric_config_entity_1 = require("../entities/fabric.config.entity");
var ui_service_1 = require("../local/ui.service");
var DtnCfgConfig = /** @class */ (function (_super) {
    __extends(DtnCfgConfig, _super);
    function DtnCfgConfig() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    return DtnCfgConfig;
}(fabric_config_entity_1.FabricConfig));
var DtnCfgUI = /** @class */ (function (_super) {
    __extends(DtnCfgUI, _super);
    function DtnCfgUI() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    return DtnCfgUI;
}(fabric_ui_entity_1.FabricUI));
var DtnCfgApiDoc = /** @class */ (function () {
    function DtnCfgApiDoc() {
        this.dtn = { isRouter: false, neighbors: [] };
    }
    return DtnCfgApiDoc;
}());
var DtnConfigComponent = /** @class */ (function () {
    function DtnConfigComponent(fabricDocApi, ui) {
        this.fabricDocApi = fabricDocApi;
        this.ui = ui;
        this.apipage = null;
        this._interval = null;
        this.nodeMap = {};
        this.neighbors = [];
        this.ui.setTitle('dtn config', 'dtn-config', 'Delay-Tolerant Network Configuration');
    }
    DtnConfigComponent.prototype.ngOnInit = function () {
        var c = new DtnCfgConfig();
        this.apipage = new fabric_apipage_entity_1.FabricApiPage(c, new DtnCfgUI(), '/dtn-config', new DtnCfgApiDoc());
        var component = this;
        this.fabricDocApi.open_singleton(this.apipage, function (ad) {
            // comonent.doSomethingInteresting(...)
            component.remap();
        });
        this._interval = setInterval(function () { component.reInit(); }, 97000);
    };
    DtnConfigComponent.prototype.reInit = function () {
        var component = this;
        this.fabricDocApi.open_singleton(this.apipage, function (ad) {
            // comonent.doSomethingInteresting(...)
            component.remap();
        });
    };
    DtnConfigComponent.prototype.remap = function () {
        this.nodeMap = {};
        this.neighbors = [];
        this.nodeMap[this.apipage.data.node.hostname] = true;
        this.nodeMap[this.apipage.data.dtn.hostname] = true;
        var i = 0;
        var l = this.apipage.data['dtn-mqtt-bridge'].length;
        for (; i < l; ++i) {
            this.nodeMap[this.apipage.data['dtn-mqtt-bridge'][i].hostname] = true;
        }
        l = this.apipage.data.dtn.neighbors.length;
        for (i = 0; i < l; ++i) {
            if (this.nodeMap[this.apipage.data.dtn.neighbors[i].name]) {
                continue;
            }
            if (/^Temporary-Neighbor-/.test(this.apipage.data.dtn.neighbors[i].name)) {
                continue;
            }
            this.neighbors.push(this.apipage.data.dtn.neighbors[i]);
        }
    };
    DtnConfigComponent.prototype.ngOnChanges = function (sc) {
    };
    DtnConfigComponent.prototype.ngOnDestroy = function () {
        clearTimeout(this._interval);
        this._interval = null;
        var component = this;
        this.fabricDocApi.call(this.apipage, 'disconnect', null, function (ad) {
            component.remap();
        });
        this.fabricDocApi.destroy(this.apipage);
        this.apipage = null;
    };
    DtnConfigComponent.prototype.classFor = function (x) {
        switch (x) {
            case "up":
                return "text-success";
            case "ko":
                return "text-warning";
            case "down":
                return "text-warning";
            case "ok":
                return "text-success";
            case "partial":
                return "text-info";
            case "error":
                return "text-danger";
            default:
                return "text-muted";
        }
    };
    DtnConfigComponent.prototype.panelClassFor = function (x) {
        switch (x) {
            case "up":
                return "panel-success";
            case "ko":
                return "panel-warning";
            case "down":
                return "panel-warning";
            case "ok":
                return "panel-success";
            case "partial":
                return "panel-info";
            case "error":
                return "panel-danger";
            default:
                return "panel-muted";
        }
    };
    DtnConfigComponent.prototype.messageFor = function (x) {
        switch (x) {
            case "up":
                return "Fully operational";
            case "ko":
                return "Offline";
            case "down":
                return "Unreachable";
            case "ok":
                return "Reachable";
            case "partial":
                return "Operational";
            case "checking":
                return "Checking...";
            case "error":
                return "Error";
            default:
                return "";
        }
    };
    DtnConfigComponent = __decorate([
        core_1.Component({
            selector: 'dtn-config-detail',
            templateUrl: 'app/views/dtn-config.component.html'
        }),
        __metadata("design:paramtypes", [fabric_docapi_service_1.FabricDocApi, ui_service_1.UIService])
    ], DtnConfigComponent);
    return DtnConfigComponent;
}());
exports.DtnConfigComponent = DtnConfigComponent;
//# sourceMappingURL=dtn-config.component.js.map