import { FabricApiDoc } from './fabric.apidoc.entity';
import { FabricConfig } from './fabric.config.entity';
import { FabricUI } from './fabric.ui.entity';
import {ApiDoc} from "./fabric.apidoc.entity";

export class FabricApiPage extends FabricApiDoc {
    constructor(config:FabricConfig, ui:FabricUI, private path:string, data:any) {
        super(config, ui, data);
    }
    getPath():string {
        return this.path;
    }
}