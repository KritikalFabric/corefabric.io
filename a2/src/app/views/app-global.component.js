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
var ui_service_1 = require("../local/ui.service");
var AppGlobalComponent = /** @class */ (function () {
    function AppGlobalComponent(ui) {
        this.ui = ui;
        this.ui.setTitle('global', 'app-global', 'our global applications');
    }
    AppGlobalComponent = __decorate([
        core_1.Component({
            selector: 'app-global-detail',
            templateUrl: 'app/views/app-global.component.html'
        }),
        __metadata("design:paramtypes", [ui_service_1.UIService])
    ], AppGlobalComponent);
    return AppGlobalComponent;
}());
exports.AppGlobalComponent = AppGlobalComponent;
//# sourceMappingURL=app-global.component.js.map