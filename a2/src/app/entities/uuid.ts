import { UUID } from 'angular2-uuid';

export class UUIDGEN
{
    private _value:any;

    constructor(value:string) {
        if (value === null) {
            this._value = UUID.UUID();
        } else {
            this._value = value;
        }
    }

    getValue():string { return this._value; }
}
