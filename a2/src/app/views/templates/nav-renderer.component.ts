import { Component, Input } from '@angular/core';

import { UIService } from '../../local/ui.service';

@Component({
    selector: 'nav-renderer',
    templateUrl: 'nav-renderer.component.html'
})
export class NavRendererComponent {
    @Input()
    item:any;

    constructor(public ui:UIService) {}
}
