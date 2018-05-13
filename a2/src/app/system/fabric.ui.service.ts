/**
 * common ui handling code
 */

import { Injectable } from '@angular/core';
import { FabricComms } from './fabric.comms.service';

@Injectable({
  providedIn: 'root',
})
export class FabricUIService {

    constructor(private fabricComms:FabricComms) {
        var service:FabricUIService = this;
        fabricComms.register(function(){
            service.showError("disconnected", null);
        },function(message){
            service.clearError();
        })
    }

    private lastErrcode:string = '';
    private lastErr:string = '';

    showError(errcode:string, err:string):string {
        console.log('SEVERE -- ' + errcode + ' [' + err + ']');
        this.lastErrcode = errcode;
        this.lastErr = err;
        return this.getError(errcode);
    }

    clearError():void {
        this.lastErrcode = '';
        this.lastErr = '';
    }

    getError(errcode:string):string {
        switch (errcode) {
            case '':
                return '';
            case 'disconnected':
                return "reconnecting...";
            case 'FABRIC-UI-00001':
                return "code 1";
            case 'FABRIC-UI-00002':
                return "code 2";
            case 'FABRIC-API-00003':
                return "code 3";
        }
        return "don't panic";
    }

    hasLastError():boolean {
        return this.lastErrcode != '';
    }

    getLastError():string {
        return this.getError(this.lastErrcode);
    }

    getLastErrorDetail():string {
        return this.lastErr;
    }

    private sidebar:boolean = false; // closed by default
    toggleSidebar():void {
        this.sidebar = !this.sidebar;
    }
    getIsSidebarOpen():boolean { return this.sidebar; }
    setIsSidebarOpen(v:boolean):void { this.sidebar = v; }
}
