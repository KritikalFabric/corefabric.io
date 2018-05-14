import { Component, enableProdMode, OnChanges, Input } from '@angular/core';

import { FabricHelpers } from './system/fabric.helpers.service';
import { FabricComms } from './system/fabric.comms.service';
import { FabricDocApi } from './system/fabric.docapi.service';
import { FabricUIService } from './system/fabric.ui.service';
import { UIService } from './local/ui.service';
import {ThemeEvent} from "./local/theme/theme.event.entity";

declare var enquire:any;

enableProdMode()
/* http://maverick.ndevrstudios.com/#/ - theme */

@Component({
  selector: 'body',
  templateUrl: 'app.component.html',
  providers: [
    FabricHelpers,
      FabricComms,
      FabricDocApi,
      FabricUIService,
      UIService
  ]
})

export class AppComponent implements OnChanges {
  constructor(private _ui:UIService) { }

  	@Input()
	public set ui(ui: UIService) { /* no-op */ }

	public get ui():UIService { return this._ui; }

  public ngOnInit() {
    let x:AppComponent = this;
    this._ui.layoutLoading=false;
    this._ui.themeEvent.subscribe(
        event => {
          switch(event.type) {
            case ThemeEvent.MAXWIDTH767: {
              x._ui.layoutIsSmallScreen = event.maxWidthFlag;
              if (event.maxWidthFlag) {
                x._ui.set('leftbarShown', false);
              } else {
                x._ui.set('leftbarCollapsed', false);
              }
            }
            break;
            case ThemeEvent.CHANGED: {
              switch (event.changedVar) {
                case 'fixedHeader': {
                  x._ui.layoutFixedHeader = x._ui.get(event.changedVar);
                }
                break;
                case 'layoutHorizontal': {
                  x._ui.layoutLayoutHorizontal = x._ui.get(event.changedVar);
                }
                break;
                case 'layoutBoxed': {
                  x._ui.layoutLayoutBoxed = x._ui.get(event.changedVar);
                }
              }
            }
            break;
          }


          /*
          $scope.$on('themeEvent:maxWidth767', function(event, newVal) {
            $timeout(function() {
              $scope.layoutIsSmallScreen = newVal;
              if (!newVal) {
                $theme.set('leftbarShown', false);
              } else {
                $theme.set('leftbarCollapsed', false);
              }
            });
          });
          $scope.$on('themeEvent:changed:fixedHeader', function(event, newVal) {
            $scope.layoutFixedHeader = newVal;
          });
          $scope.$on('themeEvent:changed:layoutHorizontal', function(event, newVal) {
            $scope.layoutLayoutHorizontal = newVal;
          });
          $scope.$on('themeEvent:changed:layoutBoxed', function(event, newVal) {
            $scope.layoutLayoutBoxed = newVal;
          });
          */
        }
    );
    enquire.register('screen and (max-width: 767px)', {
      match: function() {
        x._ui.themeEvent.emit(ThemeEvent.maxWidth767(true));
      },
      unmatch: function() {
        x._ui.themeEvent.emit(ThemeEvent.maxWidth767(false));
      }
    });
    this.applyClassesToBody();
  }

  public ngOnChanges() {
    // do nothing
  }

  public ngOnDestroy() {
    this._ui.themeEvent.unsubscribe();
    enquire.unregister('screen and (max-width: 767px)');
  }

  public applyClassesToBody() {
    let collapsed = !!this._ui.get('leftbarCollapsed');
    let body = document.getElementsByTagName('body')[0];
    if (collapsed) {
      body.classList.add('sidebar-collapsed');
    } else {
      body.classList.remove('sidebar-collapsed');
    }
  }
}
