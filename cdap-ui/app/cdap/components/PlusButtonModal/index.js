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
import { Modal, ModalHeader, ModalBody } from 'reactstrap';

import Market from 'components/Market';
import ResourceCenter from 'components/ResourceCenter';
import IconSVG from 'components/IconSVG';

import ReactCSSTransitionGroup from 'react-addons-css-transition-group';
import T from 'i18n-react';

require('./PlusButtonModal.scss');

export default class PlusButtonModal extends Component {
  constructor(props) {
    super(props);
    this.state = {
      viewMode: this.props.mode
    };

    this.closeHandler = this.closeHandler.bind(this);
  }
  toggleView(tab) {
    if (this.state.viewMode === tab) {
      return;
    }
    this.setState({
      viewMode: tab
    });
  }
  closeHandler() {
    this.setState({
      viewMode: this.props.mode
    });
    this.props.onCloseHandler();
  }

  getIconForView(view, opposite) {
    const resourceCenterClass = 'icon-resourcecenter';
    const marketIconClass = 'icon-CaskMarket';

    if (opposite) {
      return view === 'resourcecenter' ? marketIconClass : resourceCenterClass;
    } else {
      return view === 'resourcecenter' ? resourceCenterClass : marketIconClass;
    }
  }
  render() {
    const market = T.translate('commons.market');
    const resourceCenter = T.translate('commons.resource-center');

    return (
      <Modal
        isOpen={this.props.isOpen}
        toggle={this.closeHandler.bind(this)}
        className="plus-button-modal"
        size="lg"
        backdrop='static'
      >
        <ModalHeader>
          <span className="float-xs-left">
            <span className="header-icon-container">
              <IconSVG
                name={this.getIconForView(this.state.viewMode)}
                className="modal-header-icon"
              />
            </span>
            <span className="plus-modal-header-text">
            { this.state.viewMode === 'resourcecenter' ? resourceCenter : market }
            </span>
          </span>
          <div className="float-xs-right">
            <div
              className="modal-close-btn"
              onClick={this.closeHandler}
            >
              <IconSVG name="icon-close" />
            </div>
          </div>
        </ModalHeader>
        <ModalBody>
          <ReactCSSTransitionGroup
            transitionName="plus-button-modal-content"
            transitionEnterTimeout={500}
            transitionLeaveTimeout={300}
          >
            {
              this.state.viewMode === 'marketplace' ? <Market key="1"/> : <ResourceCenter key="2"/>
            }
          </ReactCSSTransitionGroup>
        </ModalBody>
      </Modal>
    );
  }
}

PlusButtonModal.defaultProps = {
  mode: 'marketplace'
};

PlusButtonModal.propTypes = {
  onCloseHandler: PropTypes.func,
  isOpen: PropTypes.bool,
  mode: PropTypes.oneOf(['marketplace', 'resourcecenter'])
};
