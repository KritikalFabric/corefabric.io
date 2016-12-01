angular
  .module('theme.core.directives')
  .directive('disableAnimation', ['$animate', function($animate) {
    'use strict';
    return {
      restrict: 'A',
      link: function($scope, $element, $attrs) {
        $attrs.$observe('disableAnimation', function(value) {
          $animate.enabled(!value, $element);
        });
      }
    };
  }])
  .directive('slideOut', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        show: '=slideOut'
      },
      link: function(scope, element) {
        element.hide();
        scope.$watch('show', function(newVal, oldVal) {
          if (newVal !== oldVal) {
            element.slideToggle({
              complete: function() {
                scope.$apply();
              }
            });
          }
        });
      }
    };
  })
  .directive('slideOutNav', ['$timeout', function($t) {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        show: '=slideOutNav'
      },
      link: function(scope, element) {
        scope.$watch('show', function(newVal) {
          if (angular.element('body').hasClass('sidebar-collapsed')) {
            if (newVal === true) {
              element.css('display', 'block');
            } else {
              element.css('display', 'none');
            }
            return;
          }
          if (newVal === true) {
            element.slideDown({
              complete: function() {
                $t(function() {
                  scope.$apply();
                });
              },
              duration: 100
            });
          } else if (newVal === false) {
            element.slideUp({
              complete: function() {
                $t(function() {
                  scope.$apply();
                });
              },
              duration: 100
            });
          }
        });
      }
    };
  }])
  .directive('fauxOffcanvas', ['EnquireService', '$window', function(EnquireService, $window) {
    'use strict';
    return {
      restrict: 'AE',
      link: function() {
        EnquireService.register('screen and (max-width: 767px)', {
            match : function() {  //smallscreen
                // angular.element('body').addClass('sidebar-collapsed');

                setWidthtoContent();
                angular.element(window).on('resize', setWidthtoContent);
            },
            unmatch : function() {  //bigscreen
                // angular.element('body').removeClass('sidebar-collapsed');

                angular.element('.static-content').css('width','');
                angular.element($window).off('resize', setWidthtoContent);
            }
        });
            
        function setWidthtoContent() {
            var w = angular.element('#wrapper').innerWidth();
            angular.element('.static-content').css('width',(w)+'px');
        }
      }
    };
  }])
  .directive('pulsate', function() {
    'use strict';
    return {
      scope: {
        pulsate: '='
      },
      link: function(scope, element) {
        // stupid hack to prevent FF from throwing error
        if (element.css('background-color') === 'transparent') {
          element.css('background-color', 'rgba(0,0,0,0.01)');
        }
        angular.element(element).pulsate(scope.pulsate);
      }
    };
  })
  .directive('prettyprint', ['$window', function($window) {
    'use strict';
    return {
      restrict: 'C',
      link: function postLink(scope, element) {
        element.html($window.prettyPrintOne(element.html(), '', true));
      }
    };
  }])
  .directive('animatePageContent', ['$rootScope', '$timeout', function($rootScope, $timeout) {
    'use strict';
    return {
      restrict: 'A',
      link: function postLink() {
        $rootScope.$on('$routeChangeSuccess', function() {
          $timeout( function () {
            var elements = angular.element('.container-fluid .animated-content');
            elements.css('visibility', 'visible')
            .velocity('transition.slideUpIn', {
              stagger: 150,
              complete: function () {
                elements.css('transform', '');
              }
            });
          }, 10);
        });
      }
    };
  }])
  .directive('passwordVerify', function() {
    'use strict';
    return {
      require: 'ngModel',
      scope: {
        passwordVerify: '='
      },
      link: function(scope, element, attrs, ctrl) {
        scope.$watch(function() {
          var combined;

          if (scope.passwordVerify || ctrl.$viewValue) {
            combined = scope.passwordVerify + '_' + ctrl.$viewValue;
          }
          return combined;
        }, function(value) {
          if (value) {
            ctrl.$parsers.unshift(function(viewValue) {
              var origin = scope.passwordVerify;
              if (origin !== viewValue) {
                ctrl.$setValidity('passwordVerify', false);
                return undefined;
              } else {
                ctrl.$setValidity('passwordVerify', true);
                return viewValue;
              }
            });
          }
        });
      }
    };
  })
  .directive('backgroundSwitcher', function() {
    'use strict';
    return {
      restrict: 'EA',
      link: function(scope, element) {
        angular.element(element).click(function() {
          angular.element('body').css('background', angular.element(element).css('background'));
        });
      }
    };
  })
  .directive('icheck', ['$timeout', function($timeout) {
    'use strict';
    return {
      require: '?ngModel',
      link: function($scope, element, $attrs, ngModel) {
        return $timeout(function() {
          var parentLabel = element.parent('label');
          if (parentLabel.length) {
            parentLabel.addClass('icheck-label');
          }
          var value;
          value = $attrs.value;

          $scope.$watch($attrs.ngModel, function() {
            angular.element(element).iCheck('update');
          });

          return angular.element(element).iCheck({
            checkboxClass: 'icheckbox_minimal-blue',
            radioClass: 'iradio_minimal-blue'
          }).on('ifChanged', function(event) {
            if (angular.element(element).attr('type') === 'checkbox' && $attrs.ngModel) {
              $scope.$apply(function() {
                return ngModel.$setViewValue(event.target.checked);
              });
            }
            if (angular.element(element).attr('type') === 'radio' && $attrs.ngModel) {
              return $scope.$apply(function() {
                return ngModel.$setViewValue(value);
              });
            }
          });
        });
      }
    };
  }])
  .directive('knob', function() {
    'use strict';
    return {
      restrict: 'EA',
      template: '<input class="dial" type="text"/>',
      scope: {
        options: '='
      },
      replace: true,
      link: function(scope, element) {
        angular.element(element).knob(scope.options);
      }
    };
  })
  .directive('uiBsSlider', ['$timeout', function($timeout) {
    'use strict';
    return {
      link: function(scope, element) {
        // $timeout is needed because certain wrapper directives don't
        // allow for a correct calculation of width
        $timeout(function() {
          element.slider();
        });
      }
    };
  }])
  .directive('tileLarge', function() {
    'use strict';
    return {
      restrict: 'E',
      scope: {
        item: '=data'
      },
      templateUrl: 'templates/tile-large.html',
      replace: true,
      transclude: true
    };
  })
  .directive('tileSimple', function() {
    'use strict';
    return {
      restrict: 'E',
      scope: {
        item: '=data'
      },
      templateUrl: 'templates/tile-simple.html',
      replace: true,
      transclude: true
    };
  })
  .directive('tileShortcut', function() {
    'use strict';
    return {
      restrict: 'E',
      scope: {
        item: '=data'
      },
      replace: true,
      templateUrl: 'templates/tile-shortcut.html'
    };
  })
  .directive('tile', function() {
    'use strict';
    return {
      restrict: 'E',
      scope: {
        heading: '@',
        type: '@'
      },
      transclude: true,
      templateUrl: 'templates/tile-generic.html',
      link: function(scope, element) {
        var heading = element.find('tile-heading');
        if (heading.length) {
          heading.appendTo(element.find('.tiles-heading'));
        }
      },
      replace: true
    };
  })
  .directive('datepaginator', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        options: '=datepaginator'
      },
      link: function(scope, element) {
        setTimeout(function() {
          element.datepaginator(scope.options);
        }, 10);
      }
    };
  })
  .directive('stickyScroll', function() {
    'use strict';
    return {
      restrict: 'A',
      link: function(scope, element, attr) {
        function stickyTop() {
          var topMax = parseInt(attr.stickyScroll);
          var headerHeight = angular.element('header').height();
          if (headerHeight > topMax) {
            topMax = headerHeight;
          }
          if (angular.element('body').hasClass('static-header') === false) {
            return element.css('top', topMax + 'px');
          }
          var windowTop = angular.element(window).scrollTop();
          if (windowTop < topMax) {
            element.css('top', (topMax - windowTop) + 'px');
          } else {
            element.css('top', 0 + 'px');
          }
        }

        angular.element(function() {
          angular.element(window).scroll(stickyTop);
          stickyTop();
        });
      }
    };
  })
  .directive('rightbarRightPosition', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        isFixedLayout: '=rightbarRightPosition'
      },
      link: function(scope) {
        scope.$watch('isFixedLayout', function(newVal, oldVal) {
          if (newVal !== oldVal) {
            setTimeout(function() {
              var $pc = angular.element('#wrapper');
              var endingRight = (angular.element(window).width() - ($pc.offset().left + $pc.outerWidth()));
              if (endingRight < 0) {
                endingRight = 0;
              }
              angular.element('.infobar').css('right', endingRight);
            }, 100);
          }
        });
      }
    };
  })
  // .directive('fitHeight', ['$window', '$timeout', '$location', function ($window, $timeout, $location) {
  // 'use strict';
  //   return {
  //     restrict: 'A',
  //     scope: true,
  //     link: function (scope, element, attr) {
  //       function resetHeight () {
  //         var horizontalNavHeight = angular.element('nav.navbar').height();
  //         var viewPortHeight = angular.element(window).height()-angular.element('header').height()-horizontalNavHeight;
  //         var contentHeight = angular.element('#page-content').height();
  //         if (viewPortHeight>contentHeight)
  //           angular.element('#page-content').css('min-height', viewPortHeight+'px');
  //       }
  //       setInterval(resetHeight, 1000);
  //       angular.element(window).on('resize', resetHeight);
  //     }
  //   };
  // }])
  .directive('backToTop', function() {
    'use strict';
    return {
      restrict: 'AE',
      link: function(scope, element) {
        element.click(function() {
          angular.element('body').scrollTop(0);
        });
      }
    };
  })
  .directive('toTopOnLoad', ['$rootScope', function($rootScope) {
    'use strict';
    return {
      restrict: 'AE',
      link: function() {
        $rootScope.$on('$routeChangeSuccess', function() {
          angular.element('body').scrollTop(0);
        });
      }
    };
  }])
  .directive('scrollToBottom', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        model: '=scrollToBottom'
      },
      link: function(scope, element) {
        scope.$watch('model', function(n, o) {
          if (n !== o) {
            element[0].scrollTop = element[0].scrollHeight;
          }
        });
      }
    };
  })
  .directive('positionChatBox', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        chatter: '=positionChatBox'
      },
      link: function(scope, element) {
        var nanoScrollOld = 0;
        angular.element('.infobar .nano-content').on('scroll', function() {
          var top = angular.element('.infobar .nano-content').scrollTop();
          var scrolledAmmount = top - nanoScrollOld;
          element.css({
            top: parseInt(element.css('top').replace('px', '')) - scrolledAmmount + 'px'
          });
          fixWindowOverflow();
          nanoScrollOld = top;
        });
        angular.element('.infobar').on('click', 'li[data-stats]', function(event) {
          angular.element(this).siblings().removeClass('active');
          angular.element(this).addClass('active');
          event.stopPropagation();
          element.css({
            top: 0,
            left: 0
          });
          var clickOffset = angular.element(event.target).closest('li[data-stats]').offset();
          element.css({
            top: clickOffset.top - angular.element(window).scrollTop() + 'px',
            left: clickOffset.left - 420 + 'px'
          });
          fixWindowOverflow();
        });

        angular.element('body').click(function() {
          angular.element('li[data-stats]').removeClass('active');
        });

        function fixWindowOverflow() {
          if (angular.element(window).height() < parseInt(element.css('top').replace('px', '')) + element.height()) {
            var offset = angular.element(window).height() - (parseInt(element.css('top').replace('px', '')) + element.height());
            element.css({
              top: parseInt(element.css('top').replace('px', '')) + offset + 'px'
            });
          } else if (parseInt(element.css('top').replace('px', '')) < 50) {
            element.css({
              top: '50px'
            });
          }
        }
      }
    };
  });