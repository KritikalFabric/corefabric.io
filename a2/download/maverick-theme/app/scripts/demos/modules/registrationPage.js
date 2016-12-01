angular
  .module('theme.demos.registration_page', [])
  .controller('RegistrationPageController', ['$scope', '$timeout', function($scope, $timeout) {
    'use strict';
    $scope.checking = false;
    $scope.checked = false;
    $scope.checkAvailability = function() {
      if ($scope.reg_form.username.$dirty === false) {
        return;
      }
      $scope.checking = true;
      $timeout(function() {
        $scope.checking = false;
        $scope.checked = true;
      }, 500);
    };
  }]);