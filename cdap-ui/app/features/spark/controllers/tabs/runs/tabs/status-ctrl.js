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

angular.module(PKG.name + '.feature.spark')
  .controller('SparkRunsDetailStatusController', function($state, $scope, MyCDAPDataSource, myHelpers) {
    var dataSrc = new MyCDAPDataSource($scope);
    var vm = this;

    vm.data = {
      'blockRemainingMemory': 0,
      'blockMaxMemory': 0,
      'blockUsedMemory': 0,
      'blockDiskSpaceUsed': 0,
      'schedulerActiveJobs': 0,
      'schedulerAllJobs': 0,
      'schedulerFailedStages': 0,
      'schedulerRunningStages': 0,
      'schedulerWaitingStages': 0
    };

    vm.runningTooltip = {
      'title': 'Running'
    };

    vm.waitingTooltip = {
      'title': 'Waiting'
    };

    vm.failedTooltip = {
      'title': 'Failed'
    };


    pollMetrics($scope.RunsController.runs.selected.runid);

    // this controller is NOT shared between the accordions.

    vm.getStagePercentage = function (type) {
      var total = (vm.data.schedulerRunningStages + vm.data.schedulerFailedStages + vm.data.schedulerWaitingStages);
      switch(type) {
        case 'running':
          return vm.data.schedulerRunningStages * 100 / total;
        case 'waiting':
          return vm.data.schedulerWaitingStages * 100 / total;
        case 'failed':
          return vm.data.schedulerFailedStages * 100 / total;
      }
    };

    function pollMetrics(runId) {
      var metricsBasePath = '/metrics/query?' +
        'tag=namespace:' + $state.params.namespace +
        '&tag=app:' + $state.params.appId +
        '&tag=spark:' + $state.params.programId +
        '&tag=run:' + runId +
        '&metric=system.driver';


      var metricPaths = {};
      metricPaths[metricsBasePath + '.BlockManager.memory.remainingMem_MB&aggregate=true'] = 'blockRemainingMemory';
      metricPaths[metricsBasePath + '.BlockManager.memory.maxMem_MB&aggregate=true'] = 'blockMaxMemory';
      metricPaths[metricsBasePath + '.BlockManager.memory.memUsed_MB&aggregate=true'] = 'blockUsedMemory';
      metricPaths[metricsBasePath + '.BlockManager.disk.diskSpaceUsed_MB&aggregate=true'] = 'blockDiskSpaceUsed';
      metricPaths[metricsBasePath + '.DAGScheduler.job.activeJobs&aggregate=true'] = 'schedulerActiveJobs';
      metricPaths[metricsBasePath + '.DAGScheduler.job.allJobs&aggregate=true'] = 'schedulerAllJobs';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.failedStages&aggregate=true'] = 'schedulerFailedStages';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.runningStages&aggregate=true'] = 'schedulerRunningStages';
      metricPaths[metricsBasePath + '.DAGScheduler.stage.waitingStages&aggregate=true'] = 'schedulerWaitingStages';

      angular.forEach(metricPaths, function (name, path) {
        dataSrc.poll({
          _cdapPath: path,
          method: 'POST',
          interval: 1000
        }, function(res) {
          vm.data[name] = myHelpers.objectQuery(res, 'series', 0, 'data', 0, 'value') || 0;
        });
      });

    }

  });
