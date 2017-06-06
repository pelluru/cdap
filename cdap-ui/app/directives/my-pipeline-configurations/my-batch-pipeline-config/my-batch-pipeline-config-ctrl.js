/*
* Copyright © 2017 Cask Data, Inc.
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

class MyBatchPipelineConfigCtrl {
  constructor(uuid, HydratorPlusPlusHydratorService, HYDRATOR_DEFAULT_VALUES, myPipelineApi, $state, myAlertOnValium) {
    this.uuid = uuid;
    this.HydratorPlusPlusHydratorService = HydratorPlusPlusHydratorService;
    this.myAlertOnValium = myAlertOnValium;

    this.engine = this.store.getEngine();
    this.engineForDisplay = this.engine === 'mapreduce' ? 'MapReduce' : 'Apache Spark Streaming';
    this.instrumentation = this.store.getInstrumentation();
    this.stageLogging = this.store.getStageLogging();
    this.numRecordsPreview = this.store.getNumRecordsPreview();
    this.startingPipeline = false;
    this.updatingPipeline = false;
    this.driverResources = {
      memoryMB: this.store.getDriverMemoryMB(),
      virtualCores: this.store.getDriverVirtualCores()
    };
    this.executorResources = {
      memoryMB: this.store.getMemoryMB(),
      virtualCores: this.store.getVirtualCores()
    };
    this.enablePipelineUpdate = false;
    this.numberConfig = {
      'widget-attributes': {
        min: 0,
        default: HYDRATOR_DEFAULT_VALUES.numOfRecordsPreview,
        showErrorMessage: false,
        convertToInteger: true
      }
    };

    this.customEngineConfig = {
      'pairs': HydratorPlusPlusHydratorService.convertMapToKeyValuePairs(this.store.getCustomConfigForDisplay())
    };

    if (this.customEngineConfig.pairs.length === 0 && !this.isDisabled) {
      this.customEngineConfig.pairs.push({
        key: '',
        value: '',
        uniqueId: 'id-' + this.uuid.v4()
      });
    }

    this.activeTab = 'runtimeArgs';

    // studio config, but not in preview mode
    if (!this.isDisabled && !this.showPreviewConfig) {
      this.activeTab = 'pipelineConfig';
    }

    this.runtimeArguments = this.checkForReset(this.runtimeArguments);
    this.onRuntimeArgumentsChange = this.onRuntimeArgumentsChange.bind(this);
    this.onCustomEngineConfigChange = this.onCustomEngineConfigChange.bind(this);
    this.getResettedRuntimeArgument = this.getResettedRuntimeArgument.bind(this);
    this.onDriverMemoryChange = this.onDriverMemoryChange.bind(this);
    this.onDriverCoreChange = this.onDriverCoreChange.bind(this);
    this.onExecutorCoreChange = this.onExecutorCoreChange.bind(this);
    this.onExecutorMemoryChange = this.onExecutorMemoryChange.bind(this);
    this.myPipelineApi = myPipelineApi;
    this.$state = $state;
    this.containsMacros = HydratorPlusPlusHydratorService.runtimeArgsContainsMacros(this.runtimeArguments);
  }

  onRuntimeArgumentsChange(newRuntimeArguments) {
    this.runtimeArguments = this.checkForReset(newRuntimeArguments);
  }

  onCustomEngineConfigChange(newCustomConfig) {
    this.customEngineConfig = newCustomConfig;
  }

  onEngineChange() {
    this.engineForDisplay = this.engine === 'mapreduce' ? 'MapReduce' : 'Apache Spark Streaming';
  }

  checkForReset(runtimeArguments) {
    let runtimeArgumentsPairs = runtimeArguments.pairs;
    for (let i = 0; i < runtimeArgumentsPairs.length; i++) {
      if (runtimeArgumentsPairs[i].notDeletable) {
        if (runtimeArgumentsPairs[i].provided) {
          runtimeArgumentsPairs[i].showReset = false;
        } else {
          let runtimeArgKey = runtimeArgumentsPairs[i].key;
          if (this.resolvedMacros.hasOwnProperty(runtimeArgKey)) {
            if (this.resolvedMacros[runtimeArgKey] !== runtimeArgumentsPairs[i].value) {
              runtimeArgumentsPairs[i].showReset = true;
            } else {
              runtimeArgumentsPairs[i].showReset = false;
            }
          }
        }
      }
    }
    return runtimeArguments;
  }

  getResettedRuntimeArgument(index) {
    let runtimeArgKey = this.runtimeArguments.pairs[index].key;
    this.runtimeArguments.pairs[index].value = this.resolvedMacros[runtimeArgKey];
    window.CaskCommon.KeyValueStore.dispatch({
      type: window.CaskCommon.KeyValueStoreActions.onUpdate,
      payload: {pairs: this.runtimeArguments.pairs}
    });
  }

  applyConfig() {
    this.applyRuntimeArguments();
    if (!this.isDisabled) {
      this.store.setEngine(this.engine);
      this.store.setCustomConfig(this.HydratorPlusPlusHydratorService.convertKeyValuePairsToMap(this.customEngineConfig));
      this.store.setInstrumentation(this.instrumentation);
      this.store.setStageLogging(this.stageLogging);
      this.store.setNumRecordsPreview(this.numRecordsPreview);
      this.store.setDriverVirtualCores(this.driverResources.virtualCores);
      this.store.setDriverMemoryMB(this.driverResources.memoryMB);
      this.store.setMemoryMB(this.executorResources.memoryMB);
      this.store.setVirtualCores(this.executorResources.virtualCores);
    }
  }

  applyAndClose() {
    this.applyConfig();
    this.onClose();
  }

  applyAndRunPipeline() {
    const applyAndRun = () => {
      this.startingPipeline = false;
      this.applyConfig();
      this.runPipeline();
    };
    this.startingPipeline = true;
    if (this.enablePipelineUpdate) {
      this.updatePipeline(false)
        .then(
          applyAndRun.bind(this),
          (err) => {
            this.startingPipeline = false;
            this.myAlertOnValium.show({
              type: 'danger',
              content: typeof err === 'object' ? JSON.stringify(err) : 'Updating pipeline failed: '+ err
            });
          }
        );
    } else {
      applyAndRun.call(this);
    }

  }

  buttonsAreDisabled() {
    let runtimeArgsMissingValues = false;
    let customConfigMissingValues = false;

    if (this.isDisabled || this.showPreviewConfig) {
      runtimeArgsMissingValues = this.HydratorPlusPlusHydratorService.keyValuePairsHaveMissingValues(this.runtimeArguments);
    }
    customConfigMissingValues = this.HydratorPlusPlusHydratorService.keyValuePairsHaveMissingValues(this.customEngineConfig);
    return runtimeArgsMissingValues || customConfigMissingValues;
  }

  onDriverMemoryChange(value) {
    this.driverResources.memoryMB = value;
    this.updatePipelineEditStatus();
  }
  onDriverCoreChange(value) {
    this.driverResources.virtualCores = value;
    this.updatePipelineEditStatus();
  }
  onExecutorCoreChange(value) {
    this.executorResources.virtualCores = value;
    this.updatePipelineEditStatus();
  }
  onExecutorMemoryChange(value) {
    this.executorResources.memoryMB = value;
    this.updatePipelineEditStatus();
  }
  onToggleInstrumentationChange() {
    this.instrumentation = !this.instrumentation;
    this.updatePipelineEditStatus();
  }
  onStageLoggingChange() {
    this.stageLogging = !this.stageLogging;
    this.updatePipelineEditStatus();
  }
  updatePipelineEditStatus() {
    const isResourcesEqual = (oldvalue, newvalue) => {
      return oldvalue.memoryMB === newvalue.memoryMB && oldvalue.virtualCores === newvalue.virtualCores;
    };
    let oldConfig = this.store.getCloneConfig();
    let updatedConfig = this.getUpdatedPipelineConfig();
    let isStageLoggingChanged = updatedConfig.config.stageLoggingEnabled !== oldConfig.config.stageLoggingEnabled;
    let isResourceModified = !isResourcesEqual(oldConfig.config.resources, updatedConfig.config.resources);
    let isDriverResourceModidified = !isResourcesEqual(oldConfig.config.driverResources, updatedConfig.config.driverResources);
    let isProcessTimingModified = oldConfig.config.processTimingEnabled !== updatedConfig.config.processTimingEnabled;
    this.enablePipelineUpdate = (
      isStageLoggingChanged ||
      isResourceModified ||
      isDriverResourceModidified ||
      isProcessTimingModified
    );
  }
  getUpdatedPipelineConfig() {

    let pipelineconfig = _.cloneDeep(this.store.getCloneConfig());
    delete pipelineconfig.__ui__;
    if (this.instrumentation) {
      pipelineconfig.config.stageLoggingEnabled = this.instrumentation;
    }
    pipelineconfig.config.resources = this.executorResources;
    pipelineconfig.config.driverResources = this.driverResources;
    pipelineconfig.config.stageLoggingEnabled = this.stageLogging;
    pipelineconfig.config.processTimingEnabled = this.instrumentation;


    return pipelineconfig;
  }
  updatePipeline(updatingPipeline = true) {
    let pipelineConfig = this.getUpdatedPipelineConfig();
    this.updatingPipeline = updatingPipeline;
    this.updatingPipeline = updatingPipeline;
    return this.myPipelineApi
      .save(
        {
          namespace: this.$state.params.namespace,
          pipeline: pipelineConfig.name
        },
        pipelineConfig
      )
      .$promise
      .then(
        () => {
          return this.$state.reload().then(() => this.updatingPipeline = false);
        },
        (err) => {
          this.updatingPipeline = false;
          this.myAlertOnValium.show({
            type: 'danger',
            content: typeof err === 'object' ? JSON.stringify(err) : 'Updating pipeline failed: '+ err
          });
        }
      );
  }
}

MyBatchPipelineConfigCtrl.$inject = ['uuid', 'HydratorPlusPlusHydratorService', 'HYDRATOR_DEFAULT_VALUES', 'myPipelineApi', '$state', 'myAlertOnValium'];
angular.module(PKG.name + '.commons')
  .directive('keyValuePairs', function(reactDirective) {
    return reactDirective(window.CaskCommon.KeyValuePairs);
  })

  .controller('MyBatchPipelineConfigCtrl', MyBatchPipelineConfigCtrl);
