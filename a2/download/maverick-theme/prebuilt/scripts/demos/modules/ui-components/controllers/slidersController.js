angular
  .module('theme.demos.ui_components')
  .controller('RangeAndSliderController', ['$scope', 'progressLoader', '$window', function($scope, progressLoader, $window) {
    'use strict';
    var _ = $window._;
    var alert = $window.alert;
    $scope.percent = 43;
    $scope.percentages = [53, 65, 23, 99];
    $scope.epOpts = [{
      animate: {
        duration: 1000,
        enabled: true
      },
      barColor: '#16a085',
      trackColor: '#edeef0',
      scaleColor: '#d2d3d6',
      lineWidth: 2,
      size: 90,
      lineCap: 'circle'
    }, {
      barColor: '#7ccc2e',
      trackColor: '#edeef0',
      scaleColor: '#d2d3d6',
      animate: {
        duration: 1000,
        enabled: true
      },
      lineWidth: 2,
      size: 90,
      lineCap: 'circle'
    }, {
      animate: {
        duration: 1000,
        enabled: true
      },
      barColor: '#e84747',
      trackColor: '#edeef0',
      scaleColor: '#d2d3d6',
      lineWidth: 2,
      size: 90,
      lineCap: 'circle'
    }, {
      barColor: '#8e44ad',
      trackColor: '#edeef0',
      scaleColor: '#d2d3d6',
      animate: {
        duration: 1000,
        enabled: true
      },
      lineWidth: 2,
      size: 90,
      lineCap: 'circle'
    }];

    $scope.randomizePie = function() {
      $scope.percentages = _.shuffle($scope.percentages);
    };

    $scope.loaderStart = function() {
      progressLoader.start();
      setTimeout(function() {
        progressLoader.set(50);
      }, 1000);

      setTimeout(function() {
        progressLoader.end();
      }, 1500);
    };
    $scope.loaderEnd = function() {
      progressLoader.end();
    };
    $scope.loaderSet = function(position) {
      progressLoader.set(position);
    };
    $scope.loaderGet = function() {
      alert(progressLoader.get() + '%');
    };
    $scope.loaderInch = function() {
      progressLoader.inch(10);
    };
  }]);