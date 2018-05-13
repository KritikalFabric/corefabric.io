/**
 * Helper methods that don't live elsewhere, e.g. where did ngCookies go?
 */

import { Injectable } from '@angular/core';

import { Http, Response } from '@angular/http';

declare var TextDecoder; // vendor/text-encoding
declare var __window; // index.html

@Injectable({
  providedIn: 'root',
})
export class FabricHelpers {
    constructor(private http: Http) { }

    getHost() {
        var s:string = __window.location.host;
        s = s.replace(/:\d+$/, '');
        return s;
    }

    getDevicePixelRatio() {
        if (__window.devicePixelRatio) {
            return __window.devicePixelRatio;
        }
        return 1.0;
    }

    getCookie(name: string) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for(var i=0;i < ca.length;i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1,c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
        }
        return null;
    }

    generateRandomUUID():string {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
            return v.toString(16);
        });
    }

    utf8Parse(input: any) {
        return (new TextDecoder('utf-8')).decode(input);
    }
}
