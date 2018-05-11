var uuid = require('node-uuid');

export class UUID
{
    private _value:any;

    constructor(value:string) {
        if (value === null) {
            this._value = uuid.v4();
        } else {
            this._value = uuid.unparse(uuid.parse(value));
        }
    }

    getValue():string { return this._value; }
}