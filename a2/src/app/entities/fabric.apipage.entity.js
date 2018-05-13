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
Object.defineProperty(exports, "__esModule", { value: true });
var fabric_apidoc_entity_1 = require("./fabric.apidoc.entity");
var FabricApiPage = /** @class */ (function (_super) {
    __extends(FabricApiPage, _super);
    function FabricApiPage(config, ui, path, data) {
        var _this = _super.call(this, config, ui, data) || this;
        _this.path = path;
        return _this;
    }
    FabricApiPage.prototype.getPath = function () {
        return this.path;
    };
    return FabricApiPage;
}(fabric_apidoc_entity_1.FabricApiDoc));
exports.FabricApiPage = FabricApiPage;
//# sourceMappingURL=fabric.apipage.entity.js.map