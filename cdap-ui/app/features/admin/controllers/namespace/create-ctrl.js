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

angular.module(PKG.name + '.feature.admin')
  .controller('NamespaceCreateController', function ($scope, $uibModalInstance, MyCDAPDataSource, myNamespace, EventPipe, myAlertOnValium, $state) {

    $scope.model = {
      name: '',
      description: ''
    };
    $scope.isSaving = false;

    var myDataSrc = new MyCDAPDataSource($scope);
    $scope.submitHandler = function() {
      if ($scope.isSaving) {
        return;
      }

      $scope.isSaving = true;
      myDataSrc.request({
        method: 'PUT',
        _cdapPath: '/namespaces/' + $scope.model.name,
        body: {
          name: $scope.model.name,
          description: $scope.model.description
        },
        suppressErrors: true
      })
        .then(
          function success(res) {
            $scope.isSaving = false;
            myNamespace.getList(true).then(function() {
              EventPipe.emit('namespace.update');

              $state.go('admin.overview', {}, { reload: true })
                .then(function() {
                  myAlertOnValium.show({
                    type: 'success',
                    content: res
                  });
                });
            });
          },
          function error(err) {
            $scope.isSaving = false;
            $scope.error = err.data;
          }
        );
    };
    $scope.closeModal = function() {
      $uibModalInstance.close();

    };

    $scope.enter = function (event) {
      if (event.keyCode === 13) {
        $scope.submitHandler();
      }
    };
  });
