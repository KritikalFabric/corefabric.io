"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var ThemeEvent = /** @class */ (function () {
    function ThemeEvent() {
        this.type = ThemeEvent.UNDEFINED;
        this.maxWidthFlag = null;
        this.changedVar = null;
    }
    ThemeEvent.maxWidth767 = function (maxWidthFlag) {
        var e = new ThemeEvent();
        e.type = ThemeEvent.MAXWIDTH767;
        e.maxWidthFlag = maxWidthFlag;
        return e;
    };
    ThemeEvent.changed = function (changedVar) {
        var e = new ThemeEvent();
        e.type = ThemeEvent.CHANGED;
        e.changedVar = changedVar;
        return e;
    };
    ThemeEvent.UNDEFINED = 0;
    ThemeEvent.MAXWIDTH767 = 1;
    ThemeEvent.CHANGED = 2;
    return ThemeEvent;
}());
exports.ThemeEvent = ThemeEvent;
//# sourceMappingURL=theme.event.entity.js.map