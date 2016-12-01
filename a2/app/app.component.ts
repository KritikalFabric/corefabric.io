import { Component, enableProdMode, OnChanges } from '@angular/core';

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
  templateUrl: 'app/app.component.html',
  providers: [
    FabricHelpers,
      FabricComms,
      FabricDocApi,
      FabricUIService,
      UIService
  ]
})

export class AppComponent implements OnChanges {
  constructor(public ui:UIService) { }

  public ngOnInit() {
    let x:AppComponent = this;
    this.ui.layoutLoading=false;
    this.ui.themeEvent.subscribe(
        event => {
          switch(event.type) {
            case ThemeEvent.MAXWIDTH767: {
              x.ui.layoutIsSmallScreen = event.maxWidthFlag;
              if (event.maxWidthFlag) {
                x.ui.set('leftbarShown', false);
              } else {
                x.ui.set('leftbarCollapsed', false);
              }
            }
            break;
            case ThemeEvent.CHANGED: {
              switch (event.changedVar) {
                case 'fixedHeader': {
                  x.ui.layoutFixedHeader = x.ui.get(event.changedVar);
                }
                break;
                case 'layoutHorizontal': {
                  x.ui.layoutLayoutHorizontal = x.ui.get(event.changedVar);
                }
                break;
                case 'layoutBoxed': {
                  x.ui.layoutLayoutBoxed = x.ui.get(event.changedVar);
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
        x.ui.themeEvent.emit(ThemeEvent.maxWidth767(true));
      },
      unmatch: function() {
        x.ui.themeEvent.emit(ThemeEvent.maxWidth767(false));
      }
    });
    this.applyClassesToBody();
  }

  public ngOnChanges() {
    // do nothing
  }

  public ngOnDestroy() {
    this.ui.themeEvent.unsubscribe();
    enquire.unregister('screen and (max-width: 767px)');
  }

  public applyClassesToBody() {
    let collapsed = !!this.ui.get('leftbarCollapsed');
    let body = document.getElementsByTagName('body')[0];
    if (collapsed) {
      body.classList.add('sidebar-collapsed');
    } else {
      body.classList.remove('sidebar-collapsed');
    }
  }
}
