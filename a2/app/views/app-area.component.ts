import { Component, Input } from '@angular/core';
import {UIService} from "../local/ui.service";

@Component({
    selector: 'app-area-detail',
    templateUrl: 'app/views/app-area.component.html'
})
export class AppAreaComponent {
    constructor(private ui:UIService) {
        this.ui.setTitle('area', 'app-area', 'our area applications');
    }
}