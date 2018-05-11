import { Component, Input } from '@angular/core';
import {UIService} from "../local/ui.service";

@Component({
    selector: 'app-global-detail',
    templateUrl: 'app/views/app-global.component.html'
})
export class AppGlobalComponent {
    constructor(private ui:UIService) {
        this.ui.setTitle('global', 'app-global', 'our global applications');
    }
}