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

import React, {PropTypes, Component} from 'react';
import NamespaceStore from 'services/NamespaceStore';
import {MyAppApi} from 'api/app';
import {MyArtifactApi} from 'api/artifact';
import {MyDatasetApi} from 'api/dataset';
import {MyStreamApi} from 'api/stream';
import FastActionButton from '../FastActionButton';
import ConfirmationModal from 'components/ConfirmationModal';
import {Tooltip} from 'reactstrap';
import ee from 'event-emitter';
import globalEvents from 'services/global-events';
import T from 'i18n-react';

export default class DeleteAction extends Component {
  constructor(props) {
    super(props);

    this.action = this.action.bind(this);
    this.toggleModal = this.toggleModal.bind(this);
    this.toggleTooltip = this.toggleTooltip.bind(this);

    this.state = {
      modal: false,
      loading: false,
      tooltipOpen: false,
      errorMessage: '',
      extendedMessage: '',
      disabled: this.props.entity.type === 'artifact' && this.props.entity.scope === 'SYSTEM'
    };
    this.eventEmitter = ee(ee);
  }

  toggleModal(event) {
    this.setState({
      modal: !this.state.modal,
      errorMessage: '',
      extendedMessage: '',
    });
    if (event) {
      event.stopPropagation();
      event.nativeEvent.stopImmediatePropagation();
    }
  }

  toggleTooltip() {
    this.setState({ tooltipOpen : !this.state.tooltipOpen});
  }

  action() {
    this.setState({loading: true});
    let api;
    let params = {
      namespace: NamespaceStore.getState().selectedNamespace
    };
    switch (this.props.entity.type) {
      case 'application':
        api = MyAppApi.delete;
        params.appId = this.props.entity.id;
        break;
      case 'artifact':
        api = MyArtifactApi.delete;
        params.artifactId = this.props.entity.id;
        params.version = this.props.entity.version;
        break;
      case 'datasetinstance':
        api = MyDatasetApi.delete;
        params.datasetId = this.props.entity.id;
        break;
      case 'stream':
        api = MyStreamApi.delete;
        params.streamId = this.props.entity.id;
        break;

    }

    api(params)
      .subscribe((res) => {
        this.props.onSuccess(res);
        this.setState({
          loading: false,
          modal: false
        });
        this.eventEmitter.emit(globalEvents.DELETEENTITY, params);
      }, (err) => {
        this.setState({
          loading: false,
          errorMessage: T.translate('features.FastAction.deleteFailed', {entityId: this.props.entity.id}),
          extendedMessage: err
        });
      });
  }

  render() {
    const actionLabel = T.translate('features.FastAction.deleteLabel');
    const headerTitle = `${actionLabel} ${this.props.entity.type}`;
    const tooltipID = `${this.props.entity.uniqueId}-delete`;

    return (
      <span className="btn btn-secondary btn-sm">
        <FastActionButton
          icon="icon-trash"
          action={this.toggleModal}
          disabled={this.state.disabled}
          id={tooltipID}
        />
        <Tooltip
          placement="top"
          className="fast-action-tooltip"
          isOpen={this.state.tooltipOpen}
          target={tooltipID}
          toggle={this.toggleTooltip}
          delay={0}
        >
          {T.translate('features.FastAction.deleteLabel')}
        </Tooltip>


        {
          this.state.modal ? (
            <ConfirmationModal
              headerTitle={headerTitle}
              toggleModal={this.toggleModal}
              confirmationText={T.translate('features.FastAction.deleteConfirmation', {entityId: this.props.entity.id})}
              confirmButtonText={actionLabel}
              confirmFn={this.action}
              cancelFn={this.toggleModal}
              isOpen={this.state.modal}
              isLoading={this.state.loading}
              errorMessage={this.state.errorMessage}
              extendedMessage={this.state.extendedMessage}
            />
          ) : null
        }
      </span>
    );
  }
}

DeleteAction.propTypes = {
  entity: PropTypes.shape({
    id: PropTypes.string.isRequired,
    uniqueId: PropTypes.string,
    version: PropTypes.string,
    scope: PropTypes.oneOf(['SYSTEM', 'USER']),
    type: PropTypes.oneOf(['application', 'artifact', 'datasetinstance', 'stream']).isRequired
  }),
  onSuccess: PropTypes.func
};
