"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var FabricApiDoc = /** @class */ (function () {
    function FabricApiDoc(config, ui, data) {
        this.config = config;
        this.ui = ui;
        this.data = data;
        this.statusListeners = [];
    } // config's usually a subclass of FabricConfig, ui of FabricUI, data of ApiDoc
    FabricApiDoc.prototype.getId = function () {
        return this.id;
    };
    return FabricApiDoc;
}());
exports.FabricApiDoc = FabricApiDoc;
var ApiDoc = /** @class */ (function () {
    function ApiDoc() {
    }
    return ApiDoc;
}());
exports.ApiDoc = ApiDoc;
//# sourceMappingURL=fabric.apidoc.entity.js.map