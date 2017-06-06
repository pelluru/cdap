/*
 * Copyright © 2016-2017 Cask Data, Inc.
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

class WizardConfigureConfirmStepCtrl {
  constructor($state, myPipelineApi, HydratorPlusPlusPluginConfigFactory, GLOBALS) {
    this.$state = $state;
    this.myPipelineApi = myPipelineApi;
    this.HydratorPlusPlusPluginConfigFactory = HydratorPlusPlusPluginConfigFactory;
    this.showLoadingIcon = true;
    this.action.properties = this.action.properties || {};
    this.requiredPropertyError = GLOBALS.en.hydrator.studio.error['GENERIC-MISSING-REQUIRED-FIELDS'];
    if (this.action && !Object.keys(this.action._backendProperties || {}).length) {
      this.pluginFetch(this.action)
        .then( () => this.showLoadingIcon = false);
    } else {
      this.fetchWidgets(this.action);
    }
  }
  // Fetching Backend Properties
  pluginFetch(action) {
    let {name, version, scope} = action.defaultArtifact;
    this.errorInConfig = false;
    let params = {
      namespace: this.$state.params.namespace,
      pipelineType: name,
      version: version,
      scope: scope,
      extensionType: action.type,
      pluginName: action.name
    };
    this.loadingPlugin = true;
    return this.myPipelineApi.fetchPostActionProperties(params)
      .$promise
      .then( (res) => {
        this.action._backendProperties = res[0].properties;
        this.fetchWidgets(action);
      });
  }

  // Fetching Widget JSON for the plugin
  fetchWidgets(action) {
    let {name, version, scope} = action.defaultArtifact;
    let artifact = {
      name,
      version,
      scope,
      key: 'widgets.' + action.name + '-' + action.type
    };
    return this.HydratorPlusPlusPluginConfigFactory
      .fetchWidgetJson(artifact.name, artifact.version, artifact.scope, artifact.key)
      .then( (widgetJson) => {
        this.noConfig = false;

        this.groupsConfig = this.HydratorPlusPlusPluginConfigFactory.generateNodeConfig(this.action._backendProperties, widgetJson);

        // Initializing default value
        angular.forEach(this.groupsConfig.groups, (group) => {
          angular.forEach(group.fields, (field) => {
            if (field.defaultValue) {
              this.action.properties[field.name] = this.action.properties[field.name] || field.defaultValue;
            }
          });
        });
        this.loadingPlugin = false;
      }, () => {
        this.loadingPlugin = false;
        this.noConfig = true;
      });
  }

  addAction(isClose) {
    var fn = this.onActionConfigure();
    if ('undefined' !== typeof fn) {
      fn.call(null, (isClose ? null : this.action));
    }
  }
  gotoPreviousStep() {
    var fn = this.onGotoPreviousStep();
    if ('undefined' !== typeof fn) {
      fn.call(null);
    }
  }
  isValid() {
    this.errorInConfig = false;
    let validateProperties = (action) => {
      let valid = true;
      angular.forEach(action._backendProperties, (value, key) => {
        if (value.required && !action.properties[key]) {
          valid = false;
        }
      });
      return valid;
    };

    if (!validateProperties(this.action)) {
      this.errorInConfig = true;
      return false;
    }
    return true;
  }
  onItemClicked(event, action) {
    event.stopPropagation();
    event.preventDefault();
    this.action = action;
    this.addAction();
  }
}

WizardConfigureConfirmStepCtrl.$inject = ['$state', 'myPipelineApi', 'HydratorPlusPlusPluginConfigFactory', 'GLOBALS'];

angular.module(PKG.name + '.commons')
  .controller('WizardConfigureConfirmStepCtrl', WizardConfigureConfirmStepCtrl);
