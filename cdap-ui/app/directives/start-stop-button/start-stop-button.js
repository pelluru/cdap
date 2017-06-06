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
  .directive('myStartStopButton', function() {
    return {
      restrict: 'E',
      scope: {
        type: '@',
        isStoppable: '@',
        isRestartable: '@',
        preferencesHandler: '&',
        runtimeHandler: '&'
      },
      templateUrl: 'start-stop-button/start-stop-button.html',
      controller: function($scope, $state, MyCDAPDataSource, myRuntimeService, myProgramPreferencesService, myAlertOnValium) {
        $scope.isStoppable = ($scope.isStoppable === 'true');
        $scope.isRestartable = ($scope.isRestartable === 'true');

        $scope.runtimeArgs = [];
        var path = '/apps/' + $state.params.appId +
                   '/' + $scope.type + '/' + $state.params.programId;
        var dataSrc = new MyCDAPDataSource($scope);

        // Poll for status
        dataSrc.poll({
          _cdapNsPath: path + '/status'
        }, function(res) {
          $scope.status = res.status;
        });

        // Do 'action'. (start/stop)
        $scope.do = function(action) {
          var requestObj = {};
          requestObj = {
            _cdapNsPath: path + '/' + action,
            method: 'POST'
          };
          if (action === 'start') {
            $scope.status = 'STARTING';
            if (Object.keys($scope.runtimeArgs).length > 0) {
              requestObj.body = $scope.runtimeArgs;
            }
          } else {
            $scope.status = 'STOPPING';
          }
          dataSrc.request(requestObj)
            .then(
              function success() {
                if ($state.includes('**.run')) {
                  // go to the most current run, /runs
                  $state.go('^', $state.params, {reload: true});
                } else {
                  $state.go($state.current, $state.params, {reload: true});
                }
              },
              function error(err) {
                myAlertOnValium.show({
                  type: 'danger',
                  title: 'Error',
                  content: angular.isObject(err) ? err.data : err
                });
              }
            );
        };

        // Delegate runtime & preferences handler
        // to the parent of the directive to handle it their own way.
        $scope.openRuntime = function() {
          var fn = $scope.runtimeHandler();
          if ('undefined' !== typeof fn) {
            fn();
          } else {
            myRuntimeService.show($scope.runtimeArgs).result.then(function(res) {
              $scope.runtimeArgs = res;
            });
          }
        };

        $scope.openPreferences = function() {
          var fn = $scope.preferencesHandler();
          if ('undefined' !== typeof fn) {
            fn();
          } else {
            myProgramPreferencesService.show($scope.type);
          }
        };
      }
    };
  });
