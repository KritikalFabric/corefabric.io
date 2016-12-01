import { Component, Input } from '@angular/core';

import { UIService } from '../../local/ui.service';

@Component({
    selector: 'nav-renderer-horizontal',
    templateUrl: 'app/views/templates/nav-renderer-horizontal.component.html'
})
export class NavRendererHorizontalComponent {
    @Input()
    item:any;

    constructor(public ui:UIService) {}
}