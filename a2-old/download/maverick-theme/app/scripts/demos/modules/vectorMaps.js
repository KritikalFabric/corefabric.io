angular.module('theme.demos.vector_maps', [
    'theme.vector_maps'
  ])
  .config(['$routeProvider', function($routeProvider) {
    'use strict';
    $routeProvider
      .when('/maps-vector', {
        templateUrl: 'views/maps-vector.html',
        resolve: {
          loadJqvmap: ['$ocLazyLoad', function($ocLazyLoad) {
            return $ocLazyLoad.load([
              'bower_components/jqvmap/jqvmap/maps/jquery.vmap.europe.js',
              'bower_components/jqvmap/jqvmap/maps/jquery.vmap.usa.js'
            ]);
          }]
        }
      });
  }])
  .controller('VectorMapsController', ['$scope', '$window', function($scope, $window) {
    'use strict';
    $scope.jqvmapWorld = {
      map: 'world_en',
      backgroundColor: 'transparent',
      color: '#f5f5f5',
      hoverOpacity: 0.7,
      selectedColor: '#1880c4',
      enableZoom: true,
      showTooltip: true,
      values: $window.sample_data,
      scaleColors: ['#90c2e3', '#4697ce'],
      normalizeFunction: 'polynomial'
    };

    $scope.jqvmapUSA = {
      map: 'usa_en',
      backgroundColor: 'transparent',
      color: '#509BBD',
      hoverOpacity: 0.7,
      selectedColor: '#1278a6',
      enableZoom: true,
      showTooltip: true,
      values: $window.sample_data,
      scaleColors: ['#C8EEFF', '#006491'],
      normalizeFunction: 'polynomial'
    };

    $scope.jqvmapEurope = {
      map: 'europe_en',
      backgroundColor: 'transparent',
      color: '#dddddd',
      hoverOpacity: 0.7,
      selectedColor: '#1dda1d',
      enableZoom: true,
      showTooltip: true,
      values: $window.sample_data,
      scaleColors: ['#b6ddb7', '#51d951'],
      normalizeFunction: 'polynomial'
    };
  }]);