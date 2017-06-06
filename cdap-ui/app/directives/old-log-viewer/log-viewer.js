/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

angular.module(PKG.name + '.commons')
  .directive('myOldLogViewer', function ($filter, $timeout, $state, $location) {

    var capitalize = $filter('caskCapitalizeFilter'),
        filterFilter = $filter('filter');

    return {
      restrict: 'E',
      scope: {
        params: '='
      },
      templateUrl: 'old-log-viewer/log-viewer.html',

      controller: function ($scope, myLogsApi, MyCDAPDataSource) {
        var dataSrc = new MyCDAPDataSource($scope);
        $scope.model = [];
        var loadTimeout = null;

        $scope.filters = 'all,info,warn,error,debug,other'.split(',')
          .map(function (key) {
            var p;
            switch(key) {
              case 'all':
                p = function() { return true; };
                break;
              case 'other':
                p = function(line) { return !(/- (INFO|WARN|ERROR|DEBUG)/).test(line.log); };
                break;
              default:
                p = function(line) { return (new RegExp('- '+key.toUpperCase())).test(line.log); };
            }
            return {
              key: key,
              label: capitalize(key),
              entries: [],
              predicate: p
            };
          });

        $scope.$watch('model', function (newVal) {
          angular.forEach($scope.filters, function (one) {
            one.entries = filterFilter(newVal, one.predicate);
          });
        });

        var params = {};
        var pollPromise = null;
        var infiniteScrollDOMElement,
            offsetDOMElement,
            container,
            logItem;


        function pollForLogs(params) {
          var path = '/namespaces/' + params.namespace +
            '/apps/' + params.appId +
            '/' + params.programType + '/' + params.programId +
            '/runs/' + params.runId +
            '/logs/prev?max=50&escape=false';

          pollPromise = dataSrc.poll({
            _cdapPath: path,
            interval: 3000
          }, function (res) {
            $scope.model = res;

            if (res.length >= 50) {
              dataSrc.stopPoll(pollPromise.__pollId__);
              pollPromise = null;
            }
          });
        }


        function initialize() {
          params = {};
          angular.copy($scope.params, params);
          params.max = 50;
          params.escape = false;
          params.scope = $scope;

          $scope.model = [];

          if (!params.runId) { return; }

          if (pollPromise) {
            dataSrc.stopPoll(pollPromise.__pollId__);
            pollPromise = null;
          }

          $scope.loadingNext = true;
          myLogsApi.prevLogs(params)
            .$promise
            .then(function (res) {
              $scope.model = res;

              if (res.length < 50) {
                pollForLogs(params);
              }

              $scope.loadingNext = false;
            });
        }

        initialize();

        $scope.$watch('params.runId', initialize);

        $scope.loadNextLogs = function () {
          if ($scope.loadingNext || $scope.loadingPrev) {
            return;
          }

          if (pollPromise) {
            dataSrc.stopPoll(pollPromise.__pollId__);
            pollPromise = null;
          }

          $scope.loadingNext = true;
          if ($scope.model.length) {
            params.fromOffset = $scope.model[$scope.model.length-1].offset;
          }

          myLogsApi.nextLogs(params)
            .$promise
            .then(function (res) {
              $scope.model = _.uniq($scope.model.concat(res));
              $scope.loadingNext = false;
            });
        };

        $scope.loadPrevLogs = function () {
          if ($scope.loadingPrev || $scope.loadingNext) {
            return;
          }

          $scope.loadingPrev = true;

          if ($scope.model.length) {
            params.fromOffset = $scope.model[0].offset;
          }

          myLogsApi.prevLogs(params)
            .$promise
            .then(function (res) {
              $scope.model = _.uniq(res.concat($scope.model));
              $scope.loadingPrev = false;

              loadTimeout = $timeout(function() {
                var container = angular.element(document.querySelector('[infinite-scroll]'))[0];
                var logItem = angular.element(document.getElementById(params.fromOffset))[0];
                container.scrollTop = logItem.offsetTop;
              });
            });
        };
        $scope.$on('$destroy', function () {
          infiniteScrollDOMElement = offsetDOMElement = container = logItem = null;
          if (loadTimeout) {
            $timeout.cancel(loadTimeout);
          }
        });

      },

      link: function (scope, element) {

        var termEl = angular.element(element[0].querySelector('.terminal')),
            QPARAM = 'filter';
        var filterTimeout = null;

        scope.setFilter = function (k) {
          var f = filterFilter(scope.filters, {key:k});
          scope.activeFilter = f.length ? f[0] : scope.filters[0];

          if (filterTimeout) {
            $timeout.cancel(filterTimeout);
          }

          filterTimeout = $timeout(function(){
            termEl.prop('scrollTop', termEl.prop('scrollHeight'));

            if(false === $state.current.reloadOnSearch) {
              var params = {};
              params[QPARAM] = scope.activeFilter.key;
              $location.search(params);
            }
          });

        };

        scope.setFilter($state.params[QPARAM]);
        scope.$on('$destroy', function () {
          termEl = null;
          if (filterTimeout) {
            $timeout.cancel(filterTimeout);
          }
        });

      }
    };
  });

