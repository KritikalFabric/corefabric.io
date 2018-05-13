import {Component, OnChanges, SimpleChanges } from '@angular/core';

import {FabricDocApi} from "../system/fabric.docapi.service";
import {FabricApiPage} from "../entities/fabric.apipage.entity";
import {FabricUI} from "../entities/fabric.ui.entity";
import {FabricConfig} from "../entities/fabric.config.entity";
import { UIService } from '../local/ui.service';

class DtnCfgConfig extends FabricConfig { }
class DtnCfgUI extends FabricUI { }
class DtnCfgApiDoc {
    dtn:any = {isRouter:false,neighbors:[]};
}

@Component({
    selector: 'dtn-config-detail',
    templateUrl: 'app/views/dtn-config.component.html'
})
export class DtnConfigComponent implements OnChanges {
    constructor(private fabricDocApi:FabricDocApi, private ui:UIService) {
        this.ui.setTitle('dtn config', 'dtn-config', 'Delay-Tolerant Network Configuration');
    }

    apipage:FabricApiPage = null;

    ngOnInit() {
        let c:DtnCfgConfig = new DtnCfgConfig();
        this.apipage = new FabricApiPage(c, new DtnCfgUI(), '/dtn-config', new DtnCfgApiDoc());
        var component:DtnConfigComponent = this;
        this.fabricDocApi.open_singleton(this.apipage, (ad)=>{
            // comonent.doSomethingInteresting(...)
            component.remap();
        });
        this._interval = setInterval(function(){component.reInit();},97000);
    }

    _interval = null;
    reInit() {
        var component:DtnConfigComponent = this;
        this.fabricDocApi.open_singleton(this.apipage, (ad)=>{
            // comonent.doSomethingInteresting(...)
            component.remap();
        });
    }

    nodeMap = {};
    neighbors = [];
    remap() {
        this.nodeMap = {};
        this.neighbors = [];
        this.nodeMap[this.apipage.data.node.hostname]=true;
        this.nodeMap[this.apipage.data.dtn.hostname]=true;
        let i = 0;
        let l = this.apipage.data['dtn-mqtt-bridge'].length;
        for (;i<l;++i) {
            this.nodeMap[this.apipage.data['dtn-mqtt-bridge'][i].hostname]=true;
        }
        l = this.apipage.data.dtn.neighbors.length;
        for (i=0;i<l;++i) {
            if (this.nodeMap[this.apipage.data.dtn.neighbors[i].name]) {
                continue;
            }
            if (/^Temporary-Neighbor-/.test(this.apipage.data.dtn.neighbors[i].name)) {
                continue;
            }
            this.neighbors.push(this.apipage.data.dtn.neighbors[i]);
        }
    }

    ngOnChanges(sc:SimpleChanges) {

    }

    ngOnDestroy() {
        clearTimeout(this._interval); this._interval = null;
        var component:DtnConfigComponent = this;
        this.fabricDocApi.call(this.apipage, 'disconnect', null, (ad)=>{
            component.remap();
        });
        this.fabricDocApi.destroy(this.apipage);
        this.apipage = null;
    }

    classFor(x:string):string {
        switch (x) {
            case "up":
                return "text-success";
            case "ko":
                return "text-warning";
            case "down":
                return "text-warning";
            case "ok":
                return "text-success";
            case "partial":
                return "text-info";
            case "error":
                return "text-danger";
            default:
                return "text-muted";
        }
    }
    panelClassFor(x:string):string {
        switch (x) {
            case "up":
                return "panel-success";
            case "ko":
                return "panel-warning";
            case "down":
                return "panel-warning";
            case "ok":
                return "panel-success";
            case "partial":
                return "panel-info";
            case "error":
                return "panel-danger";
            default:
                return "panel-muted";
        }
    }
    messageFor(x:string):string {
        switch (x) {
            case "up":
                return "Fully operational";
            case "ko":
                return "Offline";
            case "down":
                return "Unreachable";
            case "ok":
                return "Reachable";
            case "partial":
                return "Operational";
            case "checking":
                return "Checking...";
            case "error":
                return "Error";
            default:
                return "";
        }
    }
}