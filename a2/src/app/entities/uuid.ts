import * as uuid from 'uuid/v4';

export class UUIDGEN
{
  private _value:any;

  constructor(value:string) {
    if (value === null) {
      this._value = uuid();
    } else {
      this._value = value ? value : null; // FIXME uuid.unparse(uuid.parse(value));
    }
  }

  getValue():string { return this._value; }
}
