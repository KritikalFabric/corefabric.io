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
var ui_service_1 = require("../../local/ui.service");
var NavRendererHorizontalComponent = /** @class */ (function () {
    function NavRendererHorizontalComponent(ui) {
        this.ui = ui;
    }
    __decorate([
        core_1.Input(),
        __metadata("design:type", Object)
    ], NavRendererHorizontalComponent.prototype, "item", void 0);
    NavRendererHorizontalComponent = __decorate([
        core_1.Component({
            selector: 'nav-renderer-horizontal',
            templateUrl: 'app/views/templates/nav-renderer-horizontal.component.html'
        }),
        __metadata("design:paramtypes", [ui_service_1.UIService])
    ], NavRendererHorizontalComponent);
    return NavRendererHorizontalComponent;
}());
exports.NavRendererHorizontalComponent = NavRendererHorizontalComponent;
//# sourceMappingURL=nav-renderer-horizontal.component.js.map