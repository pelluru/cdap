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

import React, { Component, PropTypes } from 'react';
import { Modal, ModalHeader, ModalBody } from 'reactstrap';
import {MyArtifactApi} from 'api/artifact';
import find from 'lodash/find';
import MyDataPrepApi from 'api/dataprep';
import DataPrepStore from 'components/DataPrep/store';
import NamespaceStore from 'services/NamespaceStore';
import {findHighestVersion} from 'services/VersionRange/VersionUtilities';
import {objectQuery} from 'services/helpers';
import T from 'i18n-react';
import {getParsedSchemaForDataPrep} from 'components/SchemaEditor/SchemaHelpers';
import {directiveRequestBodyCreator} from 'components/DataPrep/helper';
import classnames from 'classnames';

const mapErrorToMessage = (e) => {
  let message = e.message;
  if (message.indexOf('invalid field name') !== -1) {
    let splitMessage = e.message.split("field name: ");
    let fieldName = objectQuery(splitMessage, 1) || e.message;
    return {
      message: T.translate('features.DataPrep.TopPanel.invalidFieldNameMessage', {fieldName}),
      remedies: `
${T.translate('features.DataPrep.TopPanel.invalidFieldNameRemedies1')}
${T.translate('features.DataPrep.TopPanel.invalidFieldNameRemedies2')}
      `
    };
  }
  return {message: e.message};
};

export default class AddToHydratorModal extends Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: true,
      batchUrl: null,
      realtimeUrl: null,
      error: null,
      workspaceId: null,
      realtimeConfig: null,
      batchConfig: null
    };
  }

  componentWillMount() {
    this.generateLinks();
  }

  generateLinks() {
    let namespace = NamespaceStore.getState().selectedNamespace;

    MyDataPrepApi.getInfo({ namespace })
      .subscribe((res) => {
        let pluginVersion = res.values[0]['plugin.version'];

        this.constructProperties(pluginVersion);
      }, (err) => {
        if (err.statusCode === 404) {
          console.log('cannot find method');
          // can't find method; use latest wrangler-transform
          this.constructProperties();
        }
      });
  }

  findWranglerArtifacts(artifacts, pluginVersion) {
    let wranglerArtifacts = artifacts.filter((artifact) => {
      if (pluginVersion) {
        return artifact.name === 'wrangler-transform' && artifact.version === pluginVersion;
      }

      return artifact.name === 'wrangler-transform';
    });

    if (wranglerArtifacts.length === 0) {
      // cannot find plugin. Error out
      this.setState({
        error: 'Cannot find wrangler-transform plugin. Please load wrangler transform from Cask Market'
      });

      return null;
    }

    let filteredArtifacts = wranglerArtifacts;

    if (!pluginVersion) {
      let highestVersion = findHighestVersion(wranglerArtifacts.map((artifact) => {
        return artifact.version;
      }), true);

      filteredArtifacts = wranglerArtifacts.filter((artifact) => {
        return artifact.version === highestVersion;
      });
    }

    let returnArtifact = filteredArtifacts[0];

    if (filteredArtifacts.length > 1) {
      returnArtifact.scope = 'USER';
    }

    return returnArtifact;
  }

  constructFileSource(artifactsList, properties) {
    if (!properties) { return null; }

    let plugin = objectQuery(properties, 'values', '0');

    let pluginName = Object.keys(plugin)[0];

    plugin = plugin[pluginName];
    let batchArtifact = find(artifactsList, { 'name': 'core-plugins' });
    let realtimeArtifact = find(artifactsList, { 'name': 'spark-plugins' });

    let batchPluginInfo = {
      name: plugin.name,
      label: plugin.name,
      type: 'batchsource',
      artifact: batchArtifact,
      properties: plugin.properties
    };

    let realtimePluginInfo = Object.assign({}, batchPluginInfo, {
      type: 'streamingsource',
      artifact: realtimeArtifact
    });

    let batchStage = {
      name: 'File',
      plugin: batchPluginInfo
    };

    let realtimeStage = {
      name: 'File',
      plugin: realtimePluginInfo
    };

    return {
      batchSource: batchStage,
      realtimeSource: realtimeStage,
      connections: [{
        from: 'File',
        to: 'Wrangler'
      }]
    };
  }

  constructDatabaseSource(artifactsList, dbInfo) {
    if (!dbInfo) { return null; }

    let batchArtifact = find(artifactsList, { 'name': 'database-plugins' });

    let plugin = objectQuery(dbInfo, 'values', 0, 'Database');

    let pluginInfo = {
      name: 'Database',
      label: plugin.name,
      type: 'batchsource',
      artifact: batchArtifact,
      properties: plugin.properties
    };

    let batchStage = {
      name: plugin.name,
      plugin: pluginInfo
    };

    return {
      batchSource: batchStage,
      connections: [{
        from: plugin.name,
        to: 'Wrangler'
      }]
    };
  }

  constructProperties(pluginVersion) {
    let namespace = NamespaceStore.getState().selectedNamespace;
    let state = DataPrepStore.getState().dataprep;
    let workspaceId = state.workspaceId;

    let requestObj = {
      namespace,
      workspaceId
    };

    let directives = state.directives;

    let requestBody = directiveRequestBodyCreator(directives);

    let rxArray = [
      MyDataPrepApi.getSchema(requestObj, requestBody)
    ];

    if (state.workspaceUri && state.workspaceUri.length > 0) {
      let specParams = {
        namespace,
        path: state.workspaceUri
      };

      rxArray.push(MyDataPrepApi.getSpecification(specParams));
    } else if (state.workspaceInfo.properties.connection === 'database') {
      let specParams = {
        namespace,
        connectionId: state.workspaceInfo.properties.connectionid,
        tableId: state.workspaceInfo.properties.id
      };

      rxArray.push(MyDataPrepApi.getDatabaseSpecification(specParams));
    }

    MyArtifactApi.list({ namespace })
      .combineLatest(rxArray)
      .subscribe((res) => {
        let batchArtifact = find(res[0], { 'name': 'cdap-data-pipeline' });
        let realtimeArtifact = find(res[0], { 'name': 'cdap-data-streams' });
        let wranglerArtifact = this.findWranglerArtifacts(res[0], pluginVersion);

        let tempSchema = {
          name: 'avroSchema',
          type: 'record',
          fields: res[1]
        };

        let properties = {
          workspaceId,
          directives: directives.join('\n'),
          schema: JSON.stringify(tempSchema),
          field: '*'
        };

        if (state.workspaceInfo.properties.connection === 'file') {
          properties.field = 'body';
        }

        try {
          getParsedSchemaForDataPrep(tempSchema);
        } catch (e) {
          let {message, remedies = null} = mapErrorToMessage(e);
          this.setState({
            error: {message, remedies},
            loading: false
          });
          return;
        }

        let wranglerStage = {
          name: 'Wrangler',
          plugin: {
            name: 'Wrangler',
            label: 'Wrangler',
            type: 'transform',
            artifact: wranglerArtifact,
            properties
          }
        };

        let connections = [];

        let realtimeStages = [wranglerStage];
        let batchStages = [wranglerStage];

        let sourceConfigs;
        if (state.workspaceInfo.properties.connection === 'file') {
          sourceConfigs = this.constructFileSource(res[0], res[2]);
        } else if (state.workspaceInfo.properties.connection === 'database') {
          sourceConfigs = this.constructDatabaseSource(res[0], res[2]);
        }

        if (sourceConfigs) {
          realtimeStages.push(sourceConfigs.realtimeSource);
          batchStages.push(sourceConfigs.batchSource);
          connections = sourceConfigs.connections;
        }

        let realtimeConfig = {
          artifact: realtimeArtifact,
          config: {
            stages: realtimeStages,
            batchInterval: '10s',
            connections
          }
        };

        let batchConfig = {
          artifact: batchArtifact,
          config: {
            stages: batchStages,
            connections
          }
        };

        let realtimeUrl = window.getHydratorUrl({
          stateName: 'hydrator.create',
          stateParams: {
            namespace,
            workspaceId,
            artifactType: realtimeArtifact.name
          }
        });

        if (state.workspaceInfo.properties.connection === 'database') {
          realtimeUrl = null;
        }

        let batchUrl = window.getHydratorUrl({
          stateName: 'hydrator.create',
          stateParams: {
            namespace,
            workspaceId,
            artifactType: batchArtifact.name
          }
        });

        this.setState({
          loading: false,
          realtimeUrl,
          batchUrl,
          workspaceId,
          realtimeConfig,
          batchConfig
        });

      }, (err) => {
        console.log('err', err);

        this.setState({
          error: objectQuery(err, 'response', 'message')  || T.translate('features.DataPrep.TopPanel.PipelineModal.defaultErrorMessage'),
          loading: false
        });
      });
  }

  render() {
    let content;

    if (this.state.loading) {
      content = (
        <div className="loading-container">
          <h4 className="text-xs-center">
            <span className="fa fa-spin fa-spinner" />
          </h4>
        </div>
      );
    } else if (this.state.error) {
      content = (
        <div>
          <div className="text-danger error-message-container loading-container">
            <span className="fa fa-exclamation-triangle"></span>
            <span>
              {typeof this.state.error === 'object' ? this.state.error.message : this.state.error}
            </span>
            <pre>
              {
                objectQuery(this.state, 'error', 'remedies') ? this.state.error.remedies : null
              }
            </pre>
          </div>
        </div>
      );
    } else {
      let realtimeDisabledTooltip;

      if (!this.state.realtimeUrl) {
        realtimeDisabledTooltip = T.translate('features.DataPrep.TopPanel.realtimeDisabledTooltip');
      }

      content = (
        <div>
          <div className="message">
            Choose the type of pipeline to create
          </div>
          <div className="action-buttons">
            <a
              href={this.state.batchUrl}
              className="btn btn-secondary"
              onClick={(() => {
                window.localStorage.setItem(this.state.workspaceId, JSON.stringify(this.state.batchConfig));
              }).bind(this)}
            >
              <i className="fa icon-ETLBatch"/>
              <span>Batch Pipeline</span>
            </a>
            <a
              href={this.state.realtimeUrl}
              className={classnames('btn btn-secondary', {
                'inactive': !this.state.realtimeUrl
              })}
              onClick={(() => {
                if (!this.state.realtimeUrl) { return; }
                window.localStorage.setItem(this.state.workspaceId, JSON.stringify(this.state.realtimeConfig));
              }).bind(this)}
              title={realtimeDisabledTooltip}
            >
              <i className="fa icon-sparkstreaming"/>
              <span>Realtime Pipeline</span>
            </a>
          </div>
        </div>
      );
    }

    return (
      <Modal
        isOpen={true}
        toggle={this.props.toggle}
        size="lg"
        className="add-to-pipeline-dataprep-modal"
      >
        <ModalHeader>
          <span>
            Add to Pipeline
          </span>

          <div
            className="close-section float-xs-right"
            onClick={this.props.toggle}
          >
            <span className="fa fa-times" />
          </div>
        </ModalHeader>
        <ModalBody>
          {content}
        </ModalBody>
      </Modal>
    );
  }
}

AddToHydratorModal.propTypes = {
  toggle: PropTypes.func
};
