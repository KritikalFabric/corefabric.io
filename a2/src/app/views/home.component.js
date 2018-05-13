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
var HomeConfig = /** @class */ (function (_super) {
    __extends(HomeConfig, _super);
    function HomeConfig() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    return HomeConfig;
}(fabric_config_entity_1.FabricConfig));
var HomeUI = /** @class */ (function (_super) {
    __extends(HomeUI, _super);
    function HomeUI() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    return HomeUI;
}(fabric_ui_entity_1.FabricUI));
var HomeApiDoc = /** @class */ (function () {
    function HomeApiDoc() {
    }
    return HomeApiDoc;
}());
var HomeComponent = /** @class */ (function () {
    function HomeComponent(fabricDocApi, ui) {
        this.fabricDocApi = fabricDocApi;
        this.ui = ui;
        this.apipage = null;
        this.ui.setTitle('home', 'home', 'Project Overview');
    }
    HomeComponent.prototype.ngOnInit = function () {
        var c = new HomeConfig();
        this.apipage = new fabric_apipage_entity_1.FabricApiPage(c, new HomeUI(), '/home', new HomeApiDoc());
        var component = this;
        this.fabricDocApi.open_singleton(this.apipage, function (ad) {
            // comonent.doSomethingInteresting(...)
        });
    };
    HomeComponent.prototype.ngOnChanges = function (sc) {
    };
    HomeComponent.prototype.ngOnDestroy = function () {
        this.fabricDocApi.destroy(this.apipage);
        this.apipage = null;
    };
    HomeComponent = __decorate([
        core_1.Component({
            selector: 'home-detail',
            templateUrl: 'app/views/home.component.html'
        }),
        __metadata("design:paramtypes", [fabric_docapi_service_1.FabricDocApi, ui_service_1.UIService])
    ], HomeComponent);
    return HomeComponent;
}());
exports.HomeComponent = HomeComponent;
//# sourceMappingURL=home.component.js.map