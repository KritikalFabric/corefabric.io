import { UUIDGEN } from './uuid';

import { FabricApiStatus } from '../system/fabric.docapi.service';

export abstract class FabricApiDoc {
    constructor(public config:any, public ui:any, public data:any) { } // config's usually a subclass of FabricConfig, ui of FabricUI, data of ApiDoc
    id:UUIDGEN;
    singleton:boolean;
    query:any;
    mqttTopic:string;
    statusListeners:FabricApiStatus[] = [];
    getId():UUIDGEN {
        return this.id;
    }
    abstract getPath():string;
}

export abstract class ApiDoc {
}
