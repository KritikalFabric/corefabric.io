import { NgModule }      from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';
import { HttpModule } from '@angular/http';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppComponent }   from './app.component';

import { HomeComponent } from './views/home.component';
import { DtnConfigComponent } from './views/dtn-config.component';
import { DtnNodeMonitorComponent } from './views/dtn-node-monitor.component';
import { AppLocalComponent } from './views/app-local.component';
import { AppAreaComponent } from './views/app-area.component';
import { AppGlobalComponent } from './views/app-global.component';

import { NavRendererComponent } from './views/templates/nav-renderer.component';
import { NavRendererHorizontalComponent } from './views/templates/nav-renderer-horizontal.component';

import { HorizontalNavFilterPipe } from './local/ui.service';

@NgModule({
    imports:      [
        BrowserModule,
        HttpModule,
        HttpClientModule,
        RouterModule.forRoot([
            {
                path: '',
                component: HomeComponent
            },
            {
                path: 'dtn-config',
                component: DtnConfigComponent
            },
            {
                path: 'node-monitor',
                component: DtnNodeMonitorComponent
            },
            {
                path: 'app-local',
                component: AppLocalComponent
            },
            {
                path: 'app-area',
                component: AppAreaComponent
            },
            {
                path: 'app-global',
                component: AppGlobalComponent
            }
        ]),
        FormsModule
    ],
    declarations: [
        AppComponent,
        HomeComponent,
        DtnConfigComponent,
        DtnNodeMonitorComponent,
        AppLocalComponent,
        AppAreaComponent,
        AppGlobalComponent,
        NavRendererComponent,
        NavRendererHorizontalComponent,
        HorizontalNavFilterPipe
    ],
    bootstrap:    [
        AppComponent
    ]
})
export class AppModule { }
