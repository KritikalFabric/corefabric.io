angular
  .module('theme.demos.ui_components')
  .controller('CarouselDemoController', ['$scope', function($scope) {
    'use strict';
    $scope.myInterval = 5000;
    var slides = $scope.slides = [];
    var images = ['carousel1.jpg', 'carousel2.jpg', 'carousel3.jpg'];
    $scope.addSlide = function() {
      slides.push({
        image: 'assets/demo/images/' + images[slides.length],
        text: images[slides.length]
      });
    };
    for (var i = 0; i < 3; i++) {
      $scope.addSlide();
    }
  }]);