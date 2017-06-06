/*
 * Copyright © 2015-2016 Cask Data, Inc.
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
  .directive('myInstanceControl', function () {

    return {
      restrict: 'E',
      controller: 'instanceControlController',
      scope: {
        basePath: '='
      },
      templateUrl: 'instance-control/instance-control.html',
    };
  })
  .controller('instanceControlController', function ($scope, MyCDAPDataSource, myAlertOnValium) {
    var myDataSrc = new MyCDAPDataSource($scope);

    myDataSrc.request({
      _cdapPath: $scope.basePath + '/instances'
    }).then(function (res) {
      if (res.instances) {
        // This is due to https://issues.cask.co/browse/CDAP-6113
        $scope.instance = {
          provisioned: res.instances,
          requested: res.instances
        };
      } else {
        $scope.instance = res;
      }
    });

    $scope.handleSet = function () {
      myDataSrc.request({
        method: 'PUT',
        _cdapPath: $scope.basePath + '/instances',
        body: {'instances': $scope.instance.requested},
        suppressErrors: true
      }).then(
        function success () {
          $scope.instance.provisioned = $scope.instance.requested;
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

  });
