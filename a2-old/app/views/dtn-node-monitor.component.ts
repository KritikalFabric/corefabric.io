import { Component, Input } from '@angular/core';
import { Http } from '@angular/http';
import {UIService} from "../local/ui.service";
import {FabricComms} from "../system/fabric.comms.service";
import { FabricHelpers } from "../system/fabric.helpers.service";

@Component({
    selector: 'dtn-node-monitor',
    templateUrl: 'app/views/dtn-node-monitor.component.html'
})
export class DtnNodeMonitorComponent {
    constructor(private ui:UIService, private comms:FabricComms, private http:Http, private fabricHelpers:FabricHelpers) {
        this.ui.setTitle('node monitor', 'node-monitor', '(on-node) MQTT monitor');
        let x:DtnNodeMonitorComponent = this;
        this.http.get('http://' + this.fabricHelpers.getHost() + ':1080/api/json/node-monitor').map(res=>res.json()).toPromise().then(o => {x.state.topic = o.topic;});
    }
    public state = {
        topic:'',
        newMessage:'',
        system:false
    };
    public messages = [];
    public ngOnInit() {
        let x:DtnNodeMonitorComponent = this;
        this.comms.subscribe('#', function(m, t){
            console.log('got ' + m + ' at ' + t);
            if (!x.state.system && /^\$/.test(t)) return;
            x.messages.push({topic:t, message:m});
            while(x.messages.length>10) {
                x.messages.shift();
            }
        }, true);
    }
    public ngOnDestroy() {
        this.comms.unsubscribe('#');
    }
    public publish() {
        if ('' == this.state.topic) return;
        let text:string = ''+this.state.newMessage;
        this.state.newMessage = '';
        this.comms.publish(this.state.topic, text, 2, false);
    }
}