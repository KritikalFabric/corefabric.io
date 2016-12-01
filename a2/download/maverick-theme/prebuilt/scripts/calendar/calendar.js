angular
  .module('theme.calendar', [])
  .directive('fullCalendar', ['$window', function($window) {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        options: '=fullCalendar',
        updateEvents: '&',
        events: '=ngModel'
      },
      link: function(scope, element) {
        var calendar;
        var defaultOptions = {
          header: {
            left: 'prev,next today',
            center: 'title',
            right: 'month,agendaWeek,agendaDay'
          },
          selectable: true,
          selectHelper: true,
          select: function(start, end, allDay) {
            var title = $window.prompt('Event Title:');
            if (title) {
              calendar.fullCalendar('renderEvent', {
                  title: title,
                  start: start,
                  end: end,
                  allDay: allDay
                },
                true // make the event "stick"
              );
            }
            calendar.fullCalendar('unselect');
          },
          eventAfterAllRender: function (args) {
            if (calendar)
              scope.updateEvents({args: calendar.fullCalendar('clientEvents')});
          },
          editable: true,
          events: [],
          buttonText: {
            prev: '<i class="fa fa-angle-left"></i>',
            next: '<i class="fa fa-angle-right"></i>',
            prevYear: '<i class="fa fa-angle-double-left"></i>', // <<
            nextYear: '<i class="fa fa-angle-double-right"></i>', // >>
            today: 'Today',
            month: 'Month',
            week: 'Week',
            day: 'Day'
          }
        };
        angular.element.extend(true, defaultOptions, scope.options);
        if (defaultOptions.droppable === true) {
          defaultOptions.drop = function(date, allDay) {
            var originalEventObject = angular.element(this).data('eventObject');
            var copiedEventObject = angular.element.extend({}, originalEventObject);
            copiedEventObject.start = date;
            copiedEventObject.allDay = allDay;
            calendar.fullCalendar('renderEvent', copiedEventObject, true);
            if (defaultOptions.removeDroppedEvent === true) {
              angular.element(this).remove();
            }
          };
        }
        calendar = angular.element(element).fullCalendar(defaultOptions);
      }
    };
  }])
  .directive('draggableEvent', function() {
    'use strict';
    return {
      restrict: 'A',
      scope: {
        eventDef: '=draggableEvent'
      },
      link: function(scope, element) {
        angular.element(element).draggable({
          zIndex: 999,
          revert: true,
          revertDuration: 0
        });
        angular.element(element).data('eventObject', scope.eventDef);
      }
    };
  });