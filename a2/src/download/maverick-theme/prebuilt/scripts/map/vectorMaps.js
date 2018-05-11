angular.module('theme.vector_maps', [])
  .directive('jqvmap', ['$timeout', function($timeout) {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        options: '=',
      },
      link: function(scope, element) {
        $timeout(function() {
          element.vectorMap(scope.options);
          scope.$on('$destroy', function() {
            element.data().mapObject.applyTransform = function() {}; // prevent acting on nonexistent object
          });
        });
      }
    };
  }]);