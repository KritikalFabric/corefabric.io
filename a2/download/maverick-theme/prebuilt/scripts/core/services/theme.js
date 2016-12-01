angular
  .module('theme.core.services')
  .service('$theme', ['$rootScope', 'EnquireService', '$document', function($rootScope, EnquireService, $document) {
    'use strict';
    this.settings = {
      fixedHeader: true,
      headerBarHidden: true,
      leftbarCollapsed: false,
      leftbarShown: false,
      rightbarCollapsed: false,
      fullscreen: false,
      layoutHorizontal: false,
      layoutHorizontalLargeIcons: false,
      layoutBoxed: false,
      showSmallSearchBar: false,
      topNavThemeClass: 'navbar-midnightblue',
      sidebarThemeClass: 'sidebar-default',
      showChatBox: false,
      pageTransitionStyle: 'fadeIn',
      dropdownTransitionStyle: 'flipInX'
    };

    var brandColors = {
      'default': '#ecf0f1',

      'inverse': '#95a5a6',
      'primary': '#3498db',
      'success': '#2ecc71',
      'warning': '#f1c40f',
      'danger': '#e74c3c',
      'info': '#1abcaf',

      'brown': '#c0392b',
      'indigo': '#9b59b6',
      'orange': '#e67e22',
      'midnightblue': '#34495e',
      'sky': '#82c4e6',
      'magenta': '#e73c68',
      'purple': '#e044ab',
      'green': '#16a085',
      'grape': '#7a869c',
      'toyo': '#556b8d',
      'alizarin': '#e74c3c'
    };

    this.getBrandColor = function(name) {
      if (brandColors[name]) {
        return brandColors[name];
      } else {
        return brandColors['default'];
      }
    };

    $document.ready(function() {
      EnquireService.register('screen and (max-width: 767px)', {
        match: function() {
          $rootScope.$broadcast('themeEvent:maxWidth767', true);
        },
        unmatch: function() {
          $rootScope.$broadcast('themeEvent:maxWidth767', false);
        }
      });
    });

    this.get = function(key) {
      return this.settings[key];
    };
    this.set = function(key, value) {
      this.settings[key] = value;
      $rootScope.$broadcast('themeEvent:changed', {
        key: key,
        value: this.settings[key]
      });
      $rootScope.$broadcast('themeEvent:changed:' + key, this.settings[key]);
    };
    this.values = function() {
      return this.settings;
    };
  }]);