angular
  .module('theme.core.panels')
  .directive('panel', function() {
    'use strict';
    return {
      restrict: 'E',
      transclude: true,
      scope: {
        panelClass: '@',
        heading: '@',
        panelIcon: '@',
        ngDrag: '@'
      },
      templateUrl: 'templates/panel.html',
      link: function(scope, element, attrs) {
        if (attrs.ngDrag === 'true') {
          element.find('.panel-heading').attr('ng-drag-handle', '');
        }
      }
    };
  })
  .directive('panelControls', [function() {
    'use strict';
    return {
      restrict: 'E',
      require: '?^tabset',
      replace: true,
      link: function(scope, element) {
        var panel = angular.element(element).closest('.panel');
        if (panel.hasClass('.ng-isolate-scope') === false) {
          angular.element(element).children().appendTo(panel.find('.panel-ctrls'));
        }
      }
    };
  }])
  .directive('panelControlCollapse', function() {
    'use strict';
    return {
      restrict: 'EAC',
      link: function(scope, element) {
        element.html('<button class="button-icon"><i class="glyphicon glyphicon-minus"></i></button>');
        element.bind('click', function() {
          angular.element(element).find('i').toggleClass('glyphicon-plus glyphicon-minus');
          angular.element(element).closest('.panel').find('.panel-body').slideToggle({
            duration: 200
          });
          angular.element(element).closest('.panel-heading').toggleClass('rounded-bottom');
        });
        return false;
      }
    };
  })
  .directive('panelControlRefresh', function() {
    'use strict';
    return {
      restrict: 'EAC',
      scope: {
        isLoading: '=',
        type: '@'
      },
      link: function(scope, element) {
        var type = (scope.type) ? scope.type : 'circular';
        element.append('<button class="button-icon"><i class="glyphicon glyphicon-refresh"></i></button>');
        element.find('button').bind('click', function() {
          element.closest('.panel')
            .append('<div class="panel-loading"><div class="panel-loader-' + type + '"></div></div>');
        });
        scope.$watch('isLoading', function(n) {
          if (n === false) {
            element.closest('.panel').find('.panel-loading').remove();
          }
        });
      }
    };
  })
  .directive('panelControlColors', ['$compile', function($compile) {
    'use strict';
    return {
      restrict: 'EAC',
      replace: true,
      link: function(scope, element) {
        var controls = '<span class="button-icon" dropdown="" dropdown-toggle="">' +
          '<i class="glyphicon glyphicon-tint"></i>' +
          '<ul class="dropdown-menu dropdown-tint" role="menu">' +
          '<li><span class="btn btn-default" data-class="panel-default"></span></li>' +
          '<li><span class="btn btn-midnightblue" data-class="panel-midnightblue"></span></li>' +
          '<li><span class="btn btn-danger" data-class="panel-danger"></span></li>' +
          '<li><span class="btn btn-success" data-class="panel-success"></span></li>' +
          '<li><span class="btn btn-primary" data-class="panel-primary"></span></li>' +
          '<li><span class="btn btn-inverse" data-class="panel-inverse"></span></li>' +
          '<li><span class="btn btn-indigo" data-class="panel-indigo"></span></li>' +
          '</ul>' +
          '</span>';
        element.append($compile(controls)(scope));
        element.find('li span').bind('click', function() {
          element.closest('.panel').removeClass(function(index, css) {
            return (css.match(/(^|\s)panel-\S+/g) || []).join(' ');
          });
          element.closest('.panel').removeClass('panel-*').addClass(angular.element(this).attr('data-class'));
        });
        return false;
      }
    };
  }])
  .directive('panelControlTitle', ['$compile', '$timeout', function($compile, $t) {
    'use strict';
    return {
      restrict: 'EAC',
      scope: true,
      link: function(scope, element) {
        var controls = '<span class="button-icon" dropdown="" dropdown-toggle="" is-open="showInputBox">' +
          '<i class="glyphicon glyphicon-edit"></i>' +
          '<ul class="dropdown-menu dropdown-edit" role="menu" ng-keyup="processKeyUp($event)">' +
          '<li><input class="form-control" type="text" ng-model="title" id="lolput" ng-click="$event.preventDefault();$event.stopPropagation()" /></li>' +
          '</ul>' +
          '</span>';
        element.append($compile(controls)(scope));
        scope.processKeyUp = function(event) {
          if (event.keyCode === 32) { // space pressed
            event.preventDefault();
          } else if (event.keyCode === 13) {
            scope.showInputBox = false;
          }
        };
        scope.$watch('showInputBox', function(n) {
          if (n) {
            $t(function() {
              element.find('input').val(element.closest('.panel').find('.panel-heading h2').text()).focus();
            }, 10);
          }
        });
        scope.$watch('title', function(n) {
          element.closest('.panel').find('.panel-heading h2').html(n);
        });
        return false;
      }
    };
  }]);