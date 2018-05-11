import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import { FabricComms } from '../system/fabric.comms.service';
import {FabricDocApi} from "../system/fabric.docapi.service";
import {FabricApiPage} from "../entities/fabric.apipage.entity";
import {FabricUI} from "../entities/fabric.ui.entity";
import {FabricConfig} from "../entities/fabric.config.entity";
import { UIService } from '../local/ui.service';

class HomeConfig extends FabricConfig { }
class HomeUI extends FabricUI { }
class HomeApiDoc {}

@Component({
    selector: 'home-detail',
    templateUrl: 'app/views/home.component.html'
})
export class HomeComponent implements OnChanges {

    constructor(private fabricDocApi:FabricDocApi, private ui:UIService) {
        this.ui.setTitle('home', 'home', 'Project Overview');
    }

    apipage:FabricApiPage = null;

    ngOnInit() {
        let c:HomeConfig = new HomeConfig();
        this.apipage = new FabricApiPage(c, new HomeUI(), '/home', new HomeApiDoc());
        var component:HomeComponent = this;
        this.fabricDocApi.open_singleton(this.apipage, (ad)=>{
            // comonent.doSomethingInteresting(...)
        });
    }

    ngOnChanges(sc:SimpleChanges) {

    }

    ngOnDestroy() {
        this.fabricDocApi.destroy(this.apipage);
        this.apipage = null;
    }


}