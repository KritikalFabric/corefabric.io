angular.module('theme.mail.inbox_controller', [])
  .controller('InboxController', ['$scope', '$location', function($scope, $location) {
    'use strict';
    $scope.openMail = function() {
      $location.path('/read-mail');
    };
  }]);