angular
  .module('theme.core.navigation_controller', ['theme.core.services'])
  .controller('NavigationController', ['$scope', '$location', '$timeout', function($scope, $location, $timeout) {
    'use strict';
    $scope.menu = [{
      label: 'Overview',
      iconClasses: '',
      separator: true
    }, {
      label: 'Dashboard',
      iconClasses: 'glyphicon glyphicon-home',
      url: '#/'
    }, {
      label: 'Layouts',
      iconClasses: 'glyphicon glyphicon-th-list',
      html: '<span class="badge badge-warning">2</span>',
      children: [{
        label: 'Grids',
        url: '#/layout-grid'
      }, {
        label: 'Horizontal Navigation',
        url: '#/layout-horizontal'
      }, {
        label: 'Horizontal Navigation 2',
        url: '#/layout-horizontal2'
      }, {
        label: 'Fixed Boxed Layout',
        url: '#/layout-fixed'
      }]
    }, {
      label: 'Bonus Apps',
      iconClasses: '',
      separator: true
    }, {
      label: 'Email',
      iconClasses: 'glyphicon glyphicon-inbox',
      html: '<span class="badge badge-indigo">3</span>',
      children: [{
        label: 'Inbox',
        url: '#/inbox'
      }, {
        label: 'Compose',
        url: '#/compose-mail'
      }, {
        label: 'Read',
        url: '#/read-mail'
      }, {
        label: 'Email Templates',
        url: '#/extras-email-templates'
      }]
    }, {
      label: 'Tasks',
      iconClasses: 'glyphicon glyphicon-ok',
      html: '<span class="badge badge-info">7</span>',
      url: '#/tasks'
    }, {
      label: 'Calendar',
      iconClasses: 'glyphicon glyphicon-calendar',
      url: '#/calendar'
    }, {
      label: 'Gallery',
      iconClasses: 'glyphicon glyphicon-th-large',
      url: '#/gallery'
    }, {
      label: 'Features',
      iconClasses: 'fa fa-home',
      separator: true
    }, {
      label: 'UI Kit',
      iconClasses: 'glyphicon glyphicon-random',
      children: [{
        label: 'Typography',
        url: '#/ui-typography'
      }, {
        label: 'Buttons',
        url: '#/ui-buttons'
      }, {
        label: 'Tables',
        url: '#/tables-basic'
      }, {
        label: 'Forms',
        url: '#/form-layout'
      }, {
        label: 'Tiles',
        url: '#/ui-tiles'
      }, {
        label: 'Modals & Bootbox',
        url: '#/ui-modals'
      }, {
        label: 'Progress Bars',
        url: '#/ui-progressbars'
      }, {
        label: 'Pagers & Pagination',
        url: '#/ui-paginations'
      }, {
        label: 'Labels & Badges',
        url: '#/ui-labelsbadges'
      }, {
        label: 'Alerts & Notifications',
        url: '#/ui-alerts'
      }, {
        label: 'Sliders & Ranges',
        url: '#/ui-sliders'
      }, {
        label: 'Tabs & Accordions',
        url: '#/ui-tabs'
      }, {
        label: 'Nestable Lists',
        url: '#/ui-nestable'
      }, {
        label: 'Misc',
        url: '#/ui-misc'
      }]
    }, {
      label: 'Panels',
      iconClasses: 'glyphicon glyphicon-cog',
      url: '#/ui-panels',
      html: '<span class="label label-danger">HOT</span>'
    }, {
      label: 'Tables',
      iconClasses: 'glyphicon glyphicon-unchecked',
      children: [{
        label: 'ngGrid',
        url: '#/tables-data'
      }, {
        label: 'Responsive Tables',
        url: '#/tables-responsive'
      }, {
        label: 'Editable Tables',
        url: '#/tables-editable'
      }]
    }, {
      label: 'Forms',
      iconClasses: 'glyphicon glyphicon-check',
      children: [{
        label: 'Components',
        url: '#/form-components'
      }, {
        label: 'Wizards',
        url: '#/form-wizard'
      }, {
        label: 'Validation',
        url: '#/form-validation'
      }, {
        label: 'Masks',
        url: '#/form-masks'
      }, {
        label: 'Multiple File Uploads',
        url: '#/form-fileupload'
      }, {
        label: 'WYSIWYG Editor',
        url: '#/form-wysiwyg'
      }, {
        label: 'Inline Editor',
        url: '#/form-xeditable'
      }, {
        label: 'Image Cropping',
        url: '#/form-imagecrop'
      }]
    }, {
      label: 'Charts',
      iconClasses: 'glyphicon glyphicon-stats',
      hideOnHorizontal: true,
      children: [{
        label: 'Flot',
        url: '#/charts-flot'
      }, {
        label: 'Morris.js',
        url: '#/charts-morrisjs'
      }, {
        label: 'Chart.js',
        url: '#/charts-chartjs'
      }, {
        label: 'nvd3 Charts',
        url: '#/charts-nvd3'
      }, {
        label: 'Sparklines',
        url: '#/charts-sparklines'
      }]
    }, {
      label: 'Maps',
      iconClasses: 'glyphicon glyphicon-map-marker',
      hideOnHorizontal: true,
      children: [{
        label: 'Google Maps',
        url: '#/maps-google'
      }, {
        label: 'Vector Maps',
        url: '#/maps-vector'
      }]
    }, {
      label: 'Pages',
      iconClasses: 'glyphicon glyphicon-file',
      hideOnHorizontal: true,
      children: [{
        label: 'Blank Page',
        url: '#/extras-blank'
      }, {
        label: 'Timeline Split',
        url: '#/extras-timeline2'
      }, {
        label: 'Invoice',
        url: '#/extras-invoice'
      }, {
        label: 'Profile',
        url: '#/extras-profile'
      }, {
        label: 'Search Page',
        url: '#/extras-search'
      }, {
        label: 'Registration',
        url: '#/extras-registration'
      }, {
        label: 'Sign Up',
        url: '#/extras-signupform'
      }, {
        label: 'Password Reset',
        url: '#/extras-forgotpassword'
      }, {
        label: 'Login 1',
        url: '#/extras-login'
      }, {
        label: 'Login 2',
        url: '#/extras-login2'
      }, {
        label: '404 Page',
        url: '#/extras-404'
      }, {
        label: '500 Page',
        url: '#/extras-500'
      }]
    }, {
      label: 'Font Icons',
      iconClasses: 'glyphicon glyphicon-fire',
      hideOnHorizontal: true,
      children: [{
        label: 'Font Awesome',
        url: '#/icons-fontawesome'
      }, {
        label: 'Glyphicons',
        url: '#/icons-glyphicons'
      }]
    }, {
      label: 'Unlimited Level Menu',
      iconClasses: 'glyphicon glyphicon-align-left',
      hideOnHorizontal: true,
      children: [{
          label: 'Menu Item 1'
        }, {
          label: 'Menu Item 2',
          children: [{
            label: 'Menu Item 2.1'
          }, {
            label: 'Menu Item 2.1',
            children: [{
              label: 'Menu Item 2.1.1'
            }, {
              label: 'Menu Item 2.1.2',
              children: [{
                label: 'And Deeper Yet!'
              }]
            }]
          }]
        }
      ]
    }];


    var setParent = function(children, parent) {
      angular.forEach(children, function(child) {
        child.parent = parent;
        if (child.children !== undefined) {
          setParent(child.children, child);
        }
      });
    };

    $scope.findItemByUrl = function(children, url) {
      for (var i = 0, length = children.length; i < length; i++) {
        if (children[i].url && children[i].url.replace('#', '') === url) {
          return children[i];
        }
        if (children[i].children !== undefined) {
          var item = $scope.findItemByUrl(children[i].children, url);
          if (item) {
            return item;
          }
        }
      }
    };

    setParent($scope.menu, null);

    $scope.openItems = []; $scope.selectedItems = []; $scope.selectedFromNavMenu = false;

    $scope.select = function(item) {
      // close open nodes
      if (item.open) {
        item.open = false;
        return;
      }
      for (var i = $scope.openItems.length - 1; i >= 0; i--) {
        $scope.openItems[i].open = false;
      }
      $scope.openItems = [];
      var parentRef = item;
      while (parentRef !== null) {
        parentRef.open = true;
        $scope.openItems.push(parentRef);
        parentRef = parentRef.parent;
      }

      // handle leaf nodes
      if (!item.children || (item.children && item.children.length < 1)) {
        $scope.selectedFromNavMenu = true;
        for (var j = $scope.selectedItems.length - 1; j >= 0; j--) {
          $scope.selectedItems[j].selected = false;
        }
        $scope.selectedItems = [];
        parentRef = item;
        while (parentRef !== null) {
          parentRef.selected = true;
          $scope.selectedItems.push(parentRef);
          parentRef = parentRef.parent;
        }
      }
    };

    $scope.highlightedItems = [];
    var highlight = function(item) {
      var parentRef = item;
      while (parentRef !== null) {
        if (parentRef.selected) {
          parentRef = null;
          continue;
        }
        parentRef.selected = true;
        $scope.highlightedItems.push(parentRef);
        parentRef = parentRef.parent;
      }
    };

    var highlightItems = function(children, query) {
      angular.forEach(children, function(child) {
        if (child.label.toLowerCase().indexOf(query) > -1) {
          highlight(child);
        }
        if (child.children !== undefined) {
          highlightItems(child.children, query);
        }
      });
    };

    // $scope.searchQuery = '';
    $scope.$watch('searchQuery', function(newVal, oldVal) {
      var currentPath = '#' + $location.path();
      if (newVal === '') {
        for (var i = $scope.highlightedItems.length - 1; i >= 0; i--) {
          if ($scope.selectedItems.indexOf($scope.highlightedItems[i]) < 0) {
            if ($scope.highlightedItems[i] && $scope.highlightedItems[i] !== currentPath) {
              $scope.highlightedItems[i].selected = false;
            }
          }
        }
        $scope.highlightedItems = [];
      } else
      if (newVal !== oldVal) {
        for (var j = $scope.highlightedItems.length - 1; j >= 0; j--) {
          if ($scope.selectedItems.indexOf($scope.highlightedItems[j]) < 0) {
            $scope.highlightedItems[j].selected = false;
          }
        }
        $scope.highlightedItems = [];
        highlightItems($scope.menu, newVal.toLowerCase());
      }
    });

    $scope.$on('$routeChangeSuccess', function() {
      if ($scope.selectedFromNavMenu === false) {
        var item = $scope.findItemByUrl($scope.menu, $location.path());
        if (item) {
          $timeout(function() {
            $scope.select(item);
          });
        }
      }
      $scope.selectedFromNavMenu = false;
      $scope.searchQuery = '';
    });
  }]);