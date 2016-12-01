import { Component, Input } from '@angular/core';
import {UIService} from "../local/ui.service";

@Component({
    selector: 'app-local-detail',
    templateUrl: 'app/views/app-local.component.html'
})
export class AppLocalComponent {
    constructor(private ui:UIService) {
        this.ui.setTitle('local', 'app-local', 'our local applications');
    }
}