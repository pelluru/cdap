/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

class HydratorPlusPlusLeftPanelCtrl {
  constructor($scope, $stateParams, rVersion, HydratorPlusPlusConfigStore, HydratorPlusPlusLeftPanelStore, HydratorPlusPlusPluginActions, DAGPlusPlusFactory, DAGPlusPlusNodesActionsFactory, NonStorePipelineErrorFactory, $uibModal, myAlertOnValium, $state, $q, rArtifacts, PluginTemplatesDirActions, HydratorPlusPlusOrderingFactory, LEFTPANELSTORE_ACTIONS, myHelpers, $timeout, mySettings) {
    this.$state = $state;
    this.$scope = $scope;
    this.$stateParams = $stateParams;
    this.HydratorPlusPlusConfigStore = HydratorPlusPlusConfigStore;
    this.DAGPlusPlusFactory = DAGPlusPlusFactory;
    this.DAGPlusPlusNodesActionsFactory = DAGPlusPlusNodesActionsFactory;
    this.NonStorePipelineErrorFactory = NonStorePipelineErrorFactory;
    this.PluginTemplatesDirActions = PluginTemplatesDirActions;
    this.rVersion = rVersion;
    this.leftpanelStore = HydratorPlusPlusLeftPanelStore;
    this.myAlertOnValium = myAlertOnValium;
    this.$q = $q;
    this.HydratorPlusPlusOrderingFactory = HydratorPlusPlusOrderingFactory;
    this.leftpanelActions = HydratorPlusPlusPluginActions;
    this.LEFTPANELSTORE_ACTIONS = LEFTPANELSTORE_ACTIONS;
    this.myHelpers = myHelpers;
    this.mySettings = mySettings;


    this.pluginsMap = [];
    this.sourcesToVersionMap = {};
    this.transformsToVersionMap = {};
    this.sinksToVersionMap = {};

    this.artifacts = rArtifacts;
    let configStoreArtifact = this.HydratorPlusPlusConfigStore.getArtifact();
    this.selectedArtifact = rArtifacts.filter( ar => ar.name === configStoreArtifact.name)[0];
    this.artifactToRevert = this.selectedArtifact;
    this.leftpanelStore.dispatch(
      this.leftpanelActions.fetchExtensions({
        namespace: $stateParams.namespace,
        pipelineType: this.selectedArtifact.name,
        version: this.rVersion.version,
        scope: this.$scope
      })
    );
    this.leftpanelStore.dispatch(
      this.leftpanelActions.fetchTemplates(
        { namespace: this.$stateParams.namespace },
        { namespace: $stateParams.namespace, pipelineType: this.selectedArtifact.name, }
      )
    );

    this.leftpanelStore.dispatch(
      this.leftpanelActions.fetchDefaultVersion()
    );

    // FIXME: We need to refactor this subscriber to be more efficient. Perform less computation in controller & more in store.
    var sub = this.leftpanelStore.subscribe( () => {
      let extensions = this.leftpanelStore.getState().extensions;
      extensions.forEach( (ext) => {
        let fetchPluginsFromMap = (ext) => {
          return this.pluginsMap.filter( pluginObj => pluginObj.name === this.HydratorPlusPlusOrderingFactory.getPluginTypeDisplayName(ext));
        };
        let isPluginTypeAlreadyFetched = (ext) => {
          return fetchPluginsFromMap(ext).filter(plugin => plugin.pluginTypes.indexOf(ext) !== -1);
        };
        if (!fetchPluginsFromMap(ext).length) {
          this.pluginsMap.push({
            name: this.HydratorPlusPlusOrderingFactory.getPluginTypeDisplayName(ext),
            plugins: [],
            pluginTypes: [] // Since we group plugin types now under one label we need ot keep track of fetchPlugins call for each plugin type.
          });
        }
        if (!isPluginTypeAlreadyFetched(ext).length) {
          fetchPluginsFromMap(ext).forEach(matchedObj => matchedObj.pluginTypes.push(ext));
          let params = {
            namespace: this.$stateParams.namespace,
            pipelineType: this.HydratorPlusPlusConfigStore.getArtifact().name,
            version: this.rVersion.version,
            extensionType: ext,
            scope: this.$scope
          };
          this.leftpanelStore.dispatch(this.leftpanelActions.fetchPlugins(ext, params));
        } else {
          fetchPluginsFromMap(ext)
            .forEach( matchedObj => {
              matchedObj.plugins = matchedObj
                .pluginTypes
                .map(pluginType => this.leftpanelStore.getState().plugins.pluginTypes[pluginType] || [])
                .reduce((prev, curr) => prev.concat(curr), []);
            });
        }
      });
      if (!extensions.length) {
        return;
      }
      this.pluginsMap = this.HydratorPlusPlusOrderingFactory.orderPluginTypes(this.pluginsMap);
    });    
    var leftPanelStoreTimeout = $timeout(() => {
      this.leftpanelStore.dispatch({
        type: this.LEFTPANELSTORE_ACTIONS.PLUGIN_DEFAULT_VERSION_CHECK_AND_UPDATE
      });
      const defaultVersionMap = this.leftpanelStore.getState().plugins.pluginToVersionMap;
      this.mySettings.set('plugin-default-version', defaultVersionMap);
    }, 10000);

    this.$uibModal = $uibModal;
    this.$scope.$on('$destroy', () => {
      this.leftpanelStore.dispatch({ type: this.LEFTPANELSTORE_ACTIONS.RESET});
      sub();
      $timeout.cancel(leftPanelStoreTimeout);
    });
  }

  onArtifactChange() {
    this._checkAndShowConfirmationModalOnDirtyState()
      .then(proceedToNextStep => {
        if (!proceedToNextStep) {
          this.selectedArtifact = this.artifactToRevert;
        } else {
          this.HydratorPlusPlusConfigStore.setState(this.HydratorPlusPlusConfigStore.getDefaults());
          this.$state.go('hydrator.create', {
            namespace: this.$state.params.namespace,
            artifactType: this.selectedArtifact.name,
            data: null,
          }, {reload: true, inherit: false});
        }
      });
  }

  _checkAndShowConfirmationModalOnDirtyState(proceedCb) {
    let goTonextStep = true;
    let isStoreDirty = this.HydratorPlusPlusConfigStore.getIsStateDirty();
    if (isStoreDirty) {
      return this.$uibModal.open({
        templateUrl: '/assets/features/hydrator/templates/create/popovers/canvas-overwrite-confirmation.html',
        size: 'lg',
        backdrop: 'static',
        keyboard: false,
        windowTopClass: 'confirm-modal hydrator-modal center',
        controller: ['$scope', 'HydratorPlusPlusConfigStore', 'HydratorPlusPlusConfigActions', function($scope, HydratorPlusPlusConfigStore, HydratorPlusPlusConfigActions) {
          $scope.isSaving = false;
          $scope.discard = () => {
            goTonextStep = true;
            if (proceedCb) {
              proceedCb();
            }
            $scope.$close();
          };
          $scope.save = () => {
            let pipelineName = HydratorPlusPlusConfigStore.getName();
            if (!pipelineName.length) {
              HydratorPlusPlusConfigActions.saveAsDraft();
              goTonextStep = false;
              $scope.$close();
              return;
            }
            var unsub = HydratorPlusPlusConfigStore.registerOnChangeListener( () => {
              let isStateDirty = HydratorPlusPlusConfigStore.getIsStateDirty();
              // This is solely used for showing the spinner icon until the modal is closed.
              if(!isStateDirty) {
                unsub();
                goTonextStep = true;
                $scope.$close();
              }
            });
            HydratorPlusPlusConfigActions.saveAsDraft();
            $scope.isSaving = true;
          };
          $scope.cancel = () => {
            $scope.$close();
            goTonextStep = false;
          };
        }]
      })
      .closed
      .then(() => {
        return goTonextStep;
      });
    } else {
      if (proceedCb) {
        proceedCb();
      }
      return this.$q.when(goTonextStep);
    }
  }
  onLeftSidePanelItemClicked(event, node) {
    event.stopPropagation();
    if (node.action === 'createTemplate') {
      this.createPluginTemplate(node.contentData, 'create');
    } else if(node.action === 'deleteTemplate') {
      this.deletePluginTemplate(node.contentData);
    } else if(node.action === 'editTemplate') {
      this.createPluginTemplate(node.contentData, 'edit');
    } else {
      this.addPluginToCanvas(event, node);
    }
  }

  deletePluginTemplate(node) {
    let templateType = this.HydratorPlusPlusConfigStore.getArtifact().name;
    this.$uibModal
      .open({
        templateUrl: '/assets/features/hydrator/templates/partial/plugin-delete-confirmation.html',
        size: 'lg',
        backdrop: 'static',
        keyboard: false,
        windowTopClass: 'confirm-modal hydrator-modal',
        controller: 'PluginTemplatesDeleteCtrl',
        resolve: {
          rNode: () => node,
          rTemplateType: () => templateType
        }
      });
  }

  createPluginTemplate(node, mode) {
    let templateType = this.HydratorPlusPlusConfigStore.getArtifact().name;
    this.$uibModal
      .open({
        templateUrl: '/assets/features/hydrator/templates/create/popovers/plugin-templates.html',
        size: 'lg',
        backdrop: 'static',
        keyboard: false,
        windowTopClass: 'plugin-templates-modal hydrator-modal',
        controller: 'PluginTemplatesCreateEditCtrl',
        resolve: {
          rTemplateType: () => templateType
        }
      })
      .rendered
      .then(() => {
        this.PluginTemplatesDirActions.init({
          templateType: node.templateType || this.selectedArtifact.name,
          pluginType: node.pluginType || node.type,
          mode: mode === 'edit'? 'edit': 'create',
          templateName: node.pluginTemplate,
          pluginName: node.pluginName || node.name
        });
      });
  }
  addPluginToCanvas(event, node) {
    const getMatchedPlugin = (plugin) => {
      if (plugin.pluginTemplate) {
        return plugin;
      }
      let item = [plugin];
      let plugins = this.leftpanelStore.getState().plugins.pluginTypes[node.type];
      let matchedPlugin = plugins.filter( plug => plug.name === node.name && !plug.pluginTemplate);
      if (matchedPlugin.length) {
        item = matchedPlugin[0].allArtifacts.filter( plug => angular.equals(plug.artifact, plugin.defaultArtifact));
      }
      return item[0];
    };
    let item;
    if (node.templateName) {
      item = node;
    } else {
      item = getMatchedPlugin(node);
      this.leftpanelStore.dispatch(
        this.leftpanelActions.updateDefaultVersion(item)
      );
    }

    this.DAGPlusPlusNodesActionsFactory.resetSelectedNode();
    let name = item.name || item.pluginTemplate;

    let filteredNodes = this.HydratorPlusPlusConfigStore.getNodes()
        .filter( node => (node.plugin.label ? node.plugin.label.indexOf(name) !== -1 : false) );
    let config;
    if (item.pluginTemplate) {
      config = {
        plugin: {
          label: (filteredNodes.length > 0 ? item.pluginTemplate + (filteredNodes.length+1): item.pluginTemplate),
          name: item.pluginName,
          artifact: item.artifact,
          properties: item.properties,
        },
        icon: this.DAGPlusPlusFactory.getIcon(item.pluginName),
        type: item.pluginType,
        outputSchema: item.outputSchema,
        inputSchema: item.inputSchema,
        pluginTemplate: item.pluginTemplate,
        description: item.description,
        lock: item.lock
      };
    } else {
      config = {
        plugin: {
          label: (filteredNodes.length > 0 ? item.name + (filteredNodes.length+1): item.name),
          artifact: item.artifact,
          name: item.name,
          properties: {}
        },
        icon: item.icon,
        description: item.description,
        type: item.type,
        warning: true
      };
    }
    this.DAGPlusPlusNodesActionsFactory.addNode(config);
  }
}

HydratorPlusPlusLeftPanelCtrl.$inject = ['$scope', '$stateParams', 'rVersion', 'HydratorPlusPlusConfigStore', 'HydratorPlusPlusLeftPanelStore', 'HydratorPlusPlusPluginActions', 'DAGPlusPlusFactory', 'DAGPlusPlusNodesActionsFactory', 'NonStorePipelineErrorFactory',  '$uibModal', 'myAlertOnValium', '$state', '$q', 'rArtifacts', 'PluginTemplatesDirActions', 'HydratorPlusPlusOrderingFactory', 'LEFTPANELSTORE_ACTIONS', 'myHelpers', '$timeout', 'mySettings'];
angular.module(PKG.name + '.feature.hydrator')
  .controller('HydratorPlusPlusLeftPanelCtrl', HydratorPlusPlusLeftPanelCtrl);
