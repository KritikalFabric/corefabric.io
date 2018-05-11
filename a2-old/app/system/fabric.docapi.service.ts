import { Injectable } from '@angular/core';

import { Http, Response, RequestOptions, Request, RequestMethod, Headers } from '@angular/http';

import { FabricComms, FabricCommsOnMessageCallback } from './fabric.comms.service';
import { FabricHelpers } from './fabric.helpers.service';
import { FabricUIService } from './fabric.ui.service';
import { FabricApiDoc } from '../entities/fabric.apidoc.entity';
import { UUID } from '../entities/uuid';

declare var Paho; // vendor/eclipse-paho

class FabricDocApiParameters {
    path:string;
    method:string;
    args:any[];
    ad:FabricApiDoc;
}

export class FabricApiStatus {
    path:string;
    stateKey:string;
    state:any;
    query:any;
    topic:string;
    statusTopic:string;
    data:any;
    complete:boolean = false;
    slowQuery:boolean = false;
    onUpdate(data:any, topic:string) {
        this.data = data;
        // TODO: update status visually somehow
    }
    onComplete() {
        this.complete = true;
    }
}

export class FabricDocApiStatus {
    params:FabricDocApiParameters;
    state:any;
    topic:string;
    statusTopic:string;
    data:any;
    complete:boolean = false;
    onUpdate(data:any) {
        this.data = data;
        // TODO: update status visually somehow
    }
    onComplete() {
        this.complete = true;
    }
}

export interface FabricDocApiCallback {
    (ad:FabricApiDoc):void
}

export interface FabricDocApiListCallback {
    (ad:FabricApiDoc[]):void
}

@Injectable()
export class FabricDocApi {
    constructor(private http:Http, private fabricComms:FabricComms, private fabricHelpers:FabricHelpers, private fabricUI:FabricUIService) {
    }

    // TODO: wire up with the UI service (notifications etc)

    public open_singleton(ad:FabricApiDoc, onDoc:FabricDocApiCallback):FabricApiDoc {
        return this.remote(ad, 'open_singleton', null, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(ad);
        });
    }

    public open(ad:FabricApiDoc, onDoc:FabricDocApiCallback):FabricApiDoc {
        return this.remote(ad, 'open', null, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(ad);
        });
    }

    public list(ad:FabricApiDoc, onDoc:FabricDocApiListCallback):FabricApiDoc {
        return this.remote(ad, 'list', null, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(data);
        });
    }

    public upsert(ad:FabricApiDoc, onDoc:FabricDocApiCallback):FabricApiDoc {
        return this.remote(ad, 'upsert', null, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(ad);
        });
    }

    public delete(ad:FabricApiDoc, onDoc:FabricDocApiCallback):FabricApiDoc {
        return this.remote(ad, 'delete', null, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(ad);
        });
    }

    public call(ad:FabricApiDoc, method:string, args:any[], onDoc:FabricDocApiCallback):FabricApiDoc {
        return this.remote(ad, method, args, (data) => {
            ad.data = Object.assign(ad.data,  data);
            onDoc(ad);
        });
    }

    private remote(ad:FabricApiDoc, method:string, args:any[], callback:FabricCommsOnMessageCallback):FabricApiDoc {
        this.destroy(ad);

        var params:FabricDocApiParameters = new FabricDocApiParameters();
        params.path = ad.getPath();
        params.method = method;
        params.args = args;
        params.ad = ad;
        var api:FabricDocApi = this;
        var headers = new Headers();
        headers.append('X-Preflight', '1');
        var options = new RequestOptions({
            method: RequestMethod.Post,
            url: 'http://' + this.fabricHelpers.getHost() + ':1080/api/doc',
            headers: headers
        });
        console.log("docapi " + method);
        //console.log("calling " + JSON.stringify(params));
        this.http
            .post(options.url, JSON.stringify(params), options)
            .subscribe(
                function(response){
                    var data = response.json();
                    if (data.success) {

                        var succeeded = false;
                        var statusListener = new FabricDocApiStatus();
                        statusListener.params = params;
                        statusListener.topic = data.topic;
                        ad.mqttTopic = data.topic;
                        statusListener.statusTopic = data.statusTopic;
                        statusListener.data = null;

                        api.fabricComms.subscribe(statusListener.statusTopic, statusListener.onUpdate);

                        api.fabricComms.subscribe(ad.mqttTopic, function (data, topic) {
                            succeeded = true;
                            var ary = ad.statusListeners;
                            ad.statusListeners = [];
                            for (var i = 0; i < ary.length; ++i) {
                                if (ary[i].statusTopic != statusListener.statusTopic)
                                    ad.statusListeners.push(ary[i]);
                            }
                            api.fabricComms.unsubscribe(statusListener.statusTopic);
                            try {
                                //console.log(JSON.stringify(data));
                                callback(data, topic);
                            }
                            catch (e) {
                                api.fabricUI.showError('FABRIC-API-00003', e);
                            }
                        });

                    } else {
                        api.fabricUI.showError(data.error, JSON.stringify(params));
                    }
                },
                function(){
                    console.log('HTTP error for ' + JSON.stringify(params));
                });
        return ad;
    }

    destroy(ad:FabricApiDoc) {
        if (ad == null) return;
        if (ad.mqttTopic) {
            this.fabricComms.unsubscribe(ad.mqttTopic);
            ad.mqttTopic = null;
        }
        if (ad.statusListeners) {
            for (let sl of ad.statusListeners) {
                this.fabricComms.unsubscribe(sl.topic);
                this.fabricComms.unsubscribe(sl.statusTopic);
            }
        }
        ad.statusListeners = [];
    }
}