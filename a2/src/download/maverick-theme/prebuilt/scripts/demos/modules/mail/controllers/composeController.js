angular.module('theme.mail.compose_controller', ['textAngular'])
  .controller('ComposeController', ['$scope', function($scope) {
    'use strict';
    $scope.mailBody = null;
  }]);