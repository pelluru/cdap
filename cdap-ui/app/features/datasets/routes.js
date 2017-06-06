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

angular.module(PKG.name + '.feature.datasets')
  .config(function($stateProvider, $urlRouterProvider, MYAUTH_ROLE) {
    $stateProvider
      .state('datasets', {
        abstract: true,
        template: '<ui-view/>',
        url: '/datasets',
        data: {
          authorizedRoles: MYAUTH_ROLE.all,
          highlightTab: 'development'
        },
        parent: 'ns'
      })

      .state('datasets.detail', {
        url: '/:datasetId',
        abstract: true,
        resolve: {
          explorableDatasets: function explorableDatasets(myExploreApi, $stateParams, $q, $filter) {
            var params = {
              namespace: $stateParams.namespace
            };
            var defer = $q.defer(),
                filterFilter = $filter('filter');

            // Checking whether dataset is explorable
            myExploreApi.list(params)
              .$promise
              .then(
                function success(res) {
                  var datasetId = $stateParams.datasetId;
                  datasetId = datasetId.replace(/[\.\-]/g, '_');

                  var match = filterFilter(res, datasetId);

                  if (match.length === 0) {
                    defer.resolve(false);
                  } else {
                    defer.resolve(true);
                  }
                },
                function error() {
                  defer.resolve(false);
                }
              );
            return defer.promise;
          }
        },
        template: '<ui-view/>'
      })
        .state('datasets.detail.overview', {
          url: '/overview',
          templateUrl: '/assets/features/datasets/templates/detail.html',
          controller: 'DatasetsDetailController',
          controllerAs: 'DetailController',
          ncyBreadcrumb: {
            skip: true
          }
        })

          .state('datasets.detail.overview.status', {
            url: '/status',
            templateUrl: '/assets/features/datasets/templates/tabs/status.html',
            controller: 'DatasetDetailStatusController',
            controllerAs: 'StatusController',
            ncyBreadcrumb: {
              parent: 'data.list',
              label: '{{$state.params.datasetId}}'
            }
          })

          .state('datasets.detail.overview.explore', {
            url: '/explore',
            templateUrl: '/assets/features/datasets/templates/tabs/explore.html',
            controller: 'DatasetExploreController',
            controllerAs: 'ExploreController',
            ncyBreadcrumb: {
              label: 'Explore',
              parent: 'datasets.detail.overview.status'
            }
          })

          .state('datasets.detail.overview.programs', {
            url: '/programs',
            templateUrl: '/assets/features/datasets/templates/tabs/programs.html',
            ncyBreadcrumb: {
              label: 'Programs',
              parent: 'datasets.detail.overview.status'
            },
            controller: 'DatasetDetailProgramsController',
            controllerAs: 'ProgramsController'
          })

          .state('datasets.detail.overview.metadata', {
            url: '/metadata',
            templateUrl: '/assets/features/datasets/templates/tabs/metadata.html',
            ncyBreadcrumb: {
              label: 'Metadata',
              parent: 'datasets.detail.overview.status'
            },
            controller: 'DatasetMetadataController',
            controllerAs: 'MetadataController'
          });
  });
