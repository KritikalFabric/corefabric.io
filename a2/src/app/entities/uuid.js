"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var uuid = require('node-uuid');
var UUID = /** @class */ (function () {
    function UUID(value) {
        if (value === null) {
            this._value = uuid.v4();
        }
        else {
            this._value = uuid.unparse(uuid.parse(value));
        }
    }
    UUID.prototype.getValue = function () { return this._value; };
    return UUID;
}());
exports.UUID = UUID;
//# sourceMappingURL=uuid.js.map