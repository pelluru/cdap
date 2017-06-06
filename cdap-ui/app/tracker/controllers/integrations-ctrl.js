/*
 * Copyright © 2016 Cask Data, Inc.
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

class TrackerIntegrationsController {
  constructor($state, myTrackerApi, $scope, myAlertOnValium, MyCDAPDataSource, MyChartHelpers, MyMetricsQueryHelper, $uibModal, CDAP_UI_CONFIG) {
    this.$state = $state;
    this.myTrackerApi = myTrackerApi;
    this.$scope = $scope;
    this.myAlertOnValium = myAlertOnValium;
    this.MyChartHelpers = MyChartHelpers;
    this.MyMetricsQueryHelper = MyMetricsQueryHelper;
    this.$uibModal = $uibModal;
    this.CDAP_UI_CONFIG = CDAP_UI_CONFIG;

    this.dataSrc = new MyCDAPDataSource($scope);

    this.navigatorSetup = {
      isOpen: false,
      isSetup: false,
      isEnabled: false,
      popoverEnabled: true
    };
    this.showConfig = false;

    this.optionalSettings = {
      navigator: false
    };

    this.navigatorInfo = {
      navigatorConfig: {
        navigatorHostName: '',
        username: '',
        password: '',
        navigatorPort: '',
        autocommit: '',
        namespace: '',
        applicationURL: '',
        fileFormat: '',
        navigatorURL: '',
        metadataParentURI: ''
      },
      auditLogConfig: {
        limit: '',
        offsetDataset: '',
        namespace: '',
        topic: ''
      }
    };

    this.originalConfig = angular.copy(this.navigatorInfo);

    this.chartSettings = {
      chartMetadata: {
        showx: false,
        showy: false,
        legend: {
          show: false,
          position: 'inset'
        }
      },
      color: {
        pattern: ['#35c853']
      },
      isLive: true,
      interval: 1000,
      aggregate: 5
    };

    this.navigatorState = {};

    this.getNavigatorApp();

    this.pollId = null;

    this.flowDetailUrl = window.getOldCDAPUrl({
      stateName: 'flows.detail',
      stateParams: {
        namespace: $state.params.namespace,
        appId: this.CDAP_UI_CONFIG.navigator.appId,
        programId: this.CDAP_UI_CONFIG.navigator.programId
      }
    });

    $scope.$watch('IntegrationsController.navigatorSetup.isOpen', () => {
      if (!this.navigatorSetup.isOpen && this.navigatorSetup.isSetup) {
        this.navigatorSetup.popoverEnabled = false;
        this.cancelSetup();
      }
      this.optionalSettings.navigator = false;
    });
  }

  getNavigatorApp() {
    let params = {
      namespace: this.$state.params.namespace,
      scope: this.$scope
    };
    this.myTrackerApi.getNavigatorApp(params)
      .$promise
      .then((res) => {
        this.navigatorSetup.isSetup = true;
        this.navigatorSetup.popoverEnabled = false;
        this.optionalSettings.navigator = false;

        let config = {};
        try {
          config = JSON.parse(res.configuration);

          this.originalConfig = angular.copy(config);

          this.navigatorInfo.navigatorConfig = config.navigatorConfig;
          this.navigatorInfo.auditLogConfig = config.auditLogConfig;

          this.getNavigatorStatus();
        } catch (e) {
          console.log('Cannot parse configuration JSON');
        }
      });
  }

  getNavigatorStatus() {
    let params = {
      namespace: this.$state.params.namespace,
      scope: this.$scope
    };

    this.myTrackerApi.getNavigatorStatus(params)
      .$promise
      .then((res) => {
        if (res.length) {
          this.navigatorState = res[0];

          if (res[0].status === 'RUNNING') {
            this.navigatorSetup.isEnabled = true;
            this.fetchMetrics();

            this.logsParams = {
              namespace: this.$state.params.namespace,
              appId: this.CDAP_UI_CONFIG.navigator.appId,
              programType: 'flows',
              programId: this.CDAP_UI_CONFIG.navigator.programId,
              runId: res[0].runid.length ? res[0].runid : ''
            };
          } else {
            this.dataSrc.stopPoll(this.pollId.__pollId__);
          }
        } else {
          this.navigatorState = {
            status: 'DISABLED'
          };
        }
      });
  }

  toggleNavigator() {
    if (this.navigatorSetup.isEnabled) {
      let modal = this.$uibModal.open({
        templateUrl: '/assets/features/tracker/templates/partial/navigator-confirm-modal.html',
        size: 'md',
        windowClass: 'navigator-confirm-modal'
      });

      modal.result.then((check) => {
        if (check === 'disable') {
          this.navigatorSetup.isEnabled = false;
          this.navigatorState.status = 'STOPPING';
          this.navigatorFlowAction('stop');
        }
      });
    } else {
      this.navigatorState.status = 'STARTING';
      this.navigatorFlowAction('start');
      this.navigatorSetup.isEnabled = true;
    }
  }

  navigatorFlowAction(action) {
    let params = {
      namespace: this.$state.params.namespace,
      action: action,
      scope: this.$scope
    };

    this.myTrackerApi.toggleNavigator(params, {})
      .$promise
      .then( () => {
        this.getNavigatorStatus();
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  fetchMetrics() {

    let metric = {
      startTime: 'now-1h',
      endTime: 'now',
      resolution: '1m',
      names: ['system.process.events.processed']
    };

    let tags = {
      namespace: this.$state.params.namespace,
      app: this.CDAP_UI_CONFIG.navigator.appId,
      flow: this.CDAP_UI_CONFIG.navigator.programId
    };

    // fetch timeseries metrics
    this.pollId = this.dataSrc.poll({
      _cdapPath: '/metrics/query',
      method: 'POST',
      body: this.MyMetricsQueryHelper.constructQuery('qid', tags, metric)
    }, (res) => {
      let processedData = this.MyChartHelpers.processData(
        res,
        'qid',
        metric.names,
        metric.resolution
      );

      processedData = this.MyChartHelpers.c3ifyData(processedData, metric, metric.names);
      this.chartData = {
        x: 'x',
        columns: processedData.columns,
        keys: {
          x: 'x'
        }
      };

      this.eventsSentAggregate = _.sum(this.chartData.columns[0]);
    });
  }

  editConfiguration(event) {
    event.stopPropagation();

    this.navigatorSetup.popoverEnabled = true;
  }

  saveNavigatorSetup() {
    this.saving = true;
    let params = {
      namespace: this.$state.params.namespace,
      scope: this.$scope
    };

    let appConfig = {
      navigatorConfig: {},
      auditLogConfig: {}
    };
    angular.forEach(this.navigatorInfo.navigatorConfig, (value, key) => {
      if (value.length) {
        appConfig.navigatorConfig[key] = value;
      }
    });
    angular.forEach(this.navigatorInfo.auditLogConfig, (value, key) => {
      if (value.length) {
        appConfig.auditLogConfig[key] = value;
      }
    });

    let config = {
      artifact: this.CDAP_UI_CONFIG.navigator.artifact,
      config: appConfig
    };

    this.myTrackerApi.deployNavigator(params, config)
      .$promise
      .then( () => {
        this.navigatorSetup.isOpen = false;
        this.saving = false;
        this.getNavigatorApp();
      }, (err) => {
        this.saving = false;
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  validateFields() {
    return this.navigatorInfo.navigatorConfig.navigatorHostName &&
    this.navigatorInfo.navigatorConfig.username &&
    this.navigatorInfo.navigatorConfig.password;
  }

  cancelSetup() {
    this.navigatorSetup.isOpen = false;
    this.navigatorInfo.navigatorConfig = angular.copy(this.originalConfig.navigatorConfig);
    this.navigatorInfo.auditLogConfig = angular.copy(this.originalConfig.auditLogConfig);
    this.optionalSettings.navigator = false;
  }

}

TrackerIntegrationsController.$inject = ['$state', 'myTrackerApi', '$scope', 'myAlertOnValium', 'MyCDAPDataSource', 'MyChartHelpers', 'MyMetricsQueryHelper', '$uibModal', 'CDAP_UI_CONFIG'];

angular.module(PKG.name + '.feature.tracker')
  .controller('TrackerIntegrationsController', TrackerIntegrationsController);
