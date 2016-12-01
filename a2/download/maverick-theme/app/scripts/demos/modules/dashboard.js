angular.module('theme.demos.dashboard', [
    'angular-skycons',
    'theme.demos.forms',
    'theme.demos.tasks'
  ])
  .controller('DashboardController', ['$scope', '$timeout', '$window', function($scope, $timeout, $window) {
    'use strict';
    var moment = $window.moment;
    var _ = $window._;
    $scope.loadingChartData = false;
    $scope.refreshAction = function() {
      $scope.loadingChartData = true;
      $timeout(function() {
        $scope.loadingChartData = false;
      }, 2000);
    };

    $scope.percentages = [53, 65, 23, 99];
    $scope.randomizePie = function() {
      $scope.percentages = _.shuffle($scope.percentages);
    };

    $scope.plotStatsData = [{
      data: [
        [1, 1500],
        [2, 2200],
        [3, 1100],
        [4, 1900],
        [5, 1300],
        [6, 1900],
        [7, 900],
        [8, 1500],
        [9, 900],
        [10, 1200],
      ],
      label: 'Page Views'
    }, {
      data: [
        [1, 3100],
        [2, 4400],
        [3, 2300],
        [4, 3800],
        [5, 2600],
        [6, 3800],
        [7, 1700],
        [8, 2900],
        [9, 1900],
        [10, 2200],
      ],
      label: 'Unique Views'
    }];

    $scope.plotStatsOptions = {
      series: {
        stack: true,
        lines: {
          // show: true,
          lineWidth: 2,
          fill: 0.1
        },
        splines: {
          show: true,
          tension: 0.3,
          fill: 0.1,
          lineWidth: 3
        },
        points: {
          show: true
        },
        shadowSize: 0
      },
      grid: {
        labelMargin: 10,
        hoverable: true,
        clickable: true,
        borderWidth: 0
      },
      tooltip: true,
      tooltipOpts: {
        defaultTheme: false,
        content: 'View Count: %y'
      },
      colors: ['#b3bcc7'],
      xaxis: {
        tickColor: 'rgba(0,0,0,0.04)',
        ticks: 10,
        tickDecimals: 0,
        autoscaleMargin: 0,
        font: {
          color: 'rgba(0,0,0,0.4)',
          size: 11
        }
      },
      yaxis: {
        tickColor: 'transparent',
        ticks: 4,
        tickDecimals: 0,
        //tickColor: 'rgba(0,0,0,0.04)',
        font: {
          color: 'rgba(0,0,0,0.4)',
          size: 11
        },
        tickFormatter: function(val) {
          if (val > 999) {
            return (val / 1000) + 'K';
          } else {
            return val;
          }
        }
      },
      legend: {
        labelBoxBorderColor: 'transparent',
      }
    };

    $scope.plotRevenueData = [{
      data: [
        [1, 1100],
        [2, 1400],
        [3, 1200],
        [4, 800],
        [5, 600],
        [6, 800],
        [7, 700],
        [8, 900],
        [9, 700],
        [10, 300]
      ],
      label: 'Revenues'
    }];

    $scope.plotRevenueOptions = {
      series: {

        // lines: {
        //     show: true,
        //     lineWidth: 1.5,
        //     fill: 0.1
        // },
        bars: {
          show: true,
          fill: 1,
          lineWidth: 0,
          barWidth: 0.6,
          align: 'center'
        },
        points: {
          show: false
        },
        shadowSize: 0
      },
      grid: {
        labelMargin: 10,
        hoverable: true,
        clickable: true,
        borderWidth: 0
      },
      tooltip: true,
      tooltipOpts: {
        defaultTheme: false,
        content: 'Revenue: %y'
      },
      colors: ['#b3bcc7'],
      xaxis: {
        tickColor: 'transparent',
        //min: -0.5,
        //max: 2.7,
        tickDecimals: 0,
        autoscaleMargin: 0,
        font: {
          color: 'rgba(0,0,0,0.4)',
          size: 11
        }
      },
      yaxis: {
        ticks: 4,
        tickDecimals: 0,
        tickColor: 'rgba(0,0,0,0.04)',
        font: {
          color: 'rgba(0,0,0,0.4)',
          size: 11
        },
        tickFormatter: function(val) {
          if (val > 999) {
            return '$' + (val / 1000) + 'K';
          } else {
            return '$' + val;
          }
        }
      },
      legend: {
        labelBoxBorderColor: 'transparent'
      }
    };

    $scope.currentPage = 1;
    $scope.itemsPerPage = 7;

    $scope.accountsInRange = function() {
      return this.userAccounts.slice(this.currentPage * 7, this.currentPage * 7 + 7);
    };

    $scope.uaHandle = function($index) {
      // console.log(ua);
      this.userAccounts.splice($index, 1);
    };

    $scope.uaHandleSelected = function() {
      this.userAccounts = _.filter(this.userAccounts, function(item) {
        return (item.rem === false || item.rem === undefined);
      });
    };

    var avatars = ['potter.png', 'tennant.png', 'johansson.png', 'jackson.png', 'jobs.png'];
    $scope.userAccounts = [{
      'picture': _.shuffle(avatars).shift(),
      'name': 'Foreman Bullock',
      'email': 'foremanbullock@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Alberta Ochoa',
      'email': 'albertaochoa@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Terry Hahn',
      'email': 'terryhahn@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Donovan Doyle',
      'email': 'donovandoyle@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Stacie Blankenship',
      'email': 'stacieblankenship@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Julie Nunez',
      'email': 'julienunez@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Lacey Farrell',
      'email': 'laceyfarrell@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Stacy Cooke',
      'email': 'stacycooke@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Teri Frost',
      'email': 'terifrost@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Dionne Payne',
      'email': 'dionnepayne@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Kaufman Garrison',
      'email': 'kaufmangarrison@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Curry Avery',
      'email': 'curryavery@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Carr Sharp',
      'email': 'carrsharp@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Cooper Scott',
      'email': 'cooperscott@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Juana Spencer',
      'email': 'juanaspencer@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Madelyn Marks',
      'email': 'madelynmarks@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Bridget Ray',
      'email': 'bridgetray@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Santos Christensen',
      'email': 'santoschristensen@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Geneva Rivers',
      'email': 'genevarivers@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Carmella Bond',
      'email': 'carmellabond@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Duke Munoz',
      'email': 'dukemunoz@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Ramos Rasmussen',
      'email': 'ramosrasmussen@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Maricela Sweeney',
      'email': 'maricelasweeney@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Carmen Riley',
      'email': 'carmenriley@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Whitfield Hartman',
      'email': 'whitfieldhartman@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Jasmine Keith',
      'email': 'jasminekeith@geemail.com'
    }, {
      'picture': _.shuffle(avatars).shift(),
      'name': 'Baker Juarez',
      'email': 'bakerjuarez@geemail.com'
    }];

    $scope.drp_start = moment().subtract(1, 'days').format('MMMM D, YYYY');
    $scope.drp_end = moment().add(31, 'days').format('MMMM D, YYYY');
    $scope.drp_options = {
      ranges: {
        'Today': [moment(), moment()],
        'Yesterday': [moment().subtract(1, 'days'), moment().subtract(1, 'days')],
        'Last 7 Days': [moment().subtract(6, 'days'), moment()],
        'Last 30 Days': [moment().subtract(29, 'days'), moment()],
        'This Month': [moment().startOf('month'), moment().endOf('month')],
        'Last Month': [moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month')]
      },
      opens: 'left',
      startDate: moment().subtract(29, 'days'),
      endDate: moment()
    };

    $scope.epDiskSpace = {
      animate: {
        duration: 0,
        enabled: false
      },
      barColor: '#e6da5c',
      trackColor: '#ebedf0',
      scaleColor: false,
      lineWidth: 5,
      size: 100,
      lineCap: 'circle'
    };

    $scope.epBandwidth = {
      animate: {
        duration: 0,
        enabled: false
      },
      barColor: '#d95762',
      trackColor: '#ebedf0',
      scaleColor: false,
      lineWidth: 5,
      size: 100,
      lineCap: 'circle'
    };

    $scope.mapspace = {
      animate: {
        duration: 0,
        enabled: false
      },
      barColor: '#ef553a',
      trackColor: '#ebedf0',
      scaleColor: false,
      lineWidth: 3,
      size: 75,
      lineCap: 'circle'
    };
  }]);