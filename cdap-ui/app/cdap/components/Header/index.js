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

import React, {Component, PropTypes} from 'react';
import {NavLink} from 'react-router-dom';
import T from 'i18n-react';
import NamespaceStore from 'services/NamespaceStore';
import NamespaceDropdown from 'components/NamespaceDropdown';
import ProductDropdown from 'components/Header/ProductDropdown';
import MetadataDropdown from 'components/Header/MetadataDropdown';
import CaskMarketButton from 'components/Header/CaskMarketButton';
import {MyNamespaceApi} from 'api/namespace';
import NamespaceActions from 'services/NamespaceStore/NamespaceActions';
import classnames from 'classnames';
import ee from 'event-emitter';
import globalEvents from 'services/global-events';
import getLastSelectedNamespace from 'services/get-last-selected-namespace';

require('./Header.scss');

export default class Header extends Component {
  constructor(props) {
    super(props);
    this.state = {
      toggleNavbar: false,
      currentNamespace: null,
      metadataDropdown: false
    };
    this.namespacesubscription = null;
    this.eventEmitter = ee(ee);
  }
  componentWillMount() {
    // Polls for namespace data
    this.namespacesubscription = MyNamespaceApi.pollList()
      .subscribe(
        (res) => {
          if (res.length > 0) {
            NamespaceStore.dispatch({
              type: NamespaceActions.updateNamespaces,
              payload: {
                namespaces : res
              }
            });
          } else {
            // TL;DR - This is emitted for Authorization in main.js
            // This means there is no namespace for the user to work on.
            // which indicates she/he have no authorization for any namesapce in the system.
            this.eventEmitter.emit(globalEvents.NONAMESPACE);
          }
        }
      );
    this.nsSubscription = NamespaceStore.subscribe(() => {
      let selectedNamespace = getLastSelectedNamespace();
      if (selectedNamespace !== this.state.currentNamespace) {
        this.setState({
          currentNamespace: selectedNamespace
        });
      }
    });
  }
  componentWillUnmount() {
    this.nsSubscription();
    if (this.namespacesubscription) {
      this.namespacesubscription.dispose();
    }
  }
  toggleNavbar() {
    this.setState({
      toggleNavbar: !this.state.toggleNavbar
    });
  }

  render() {
    let baseCDAPURL = window.getAbsUIUrl({
      namespace: this.state.currentNamespace
    });
    let overviewUrl = `${baseCDAPURL}/ns/${this.state.currentNamespace}`;
    let pipelinesListUrl =  window.getHydratorUrl({
      stateName: 'hydrator.list',
      stateParams: {
        namespace: this.state.currentNamespace,
        page: 1,
        sortBy: '_stats.lastStartTime'
      }
    });
    let isPipelinesViewActive = location.pathname.indexOf('/pipelines/') !== -1;

    let dataprepUrl = window.getDataPrepUrl({
      stateName: 'workspaces',
      stateParams: {
        namespace: this.state.currentNamespace
      }
    });

    const isCDAPActive = (match, location) => {
      if (!match) { return false; }
      if (match.isExact) { return true; }

      let basePath = `/ns/${this.state.currentNamespace}`;
      let dataprepBasePath = `/ns/${this.state.currentNamespace}/dataprep`;
      let connectionsBasePath = `/ns/${this.state.currentNamespace}/connections`;
      if (
        location.pathname.startsWith(basePath) &&
        !location.pathname.startsWith(dataprepBasePath) &&
        !location.pathname.startsWith(connectionsBasePath)
      ) {
        return true;
      }
      return false;
    };

    const isDataPrepActive = (match, location) => {
      let dataprepBasePath = `/ns/${this.state.currentNamespace}/dataprep`;
      let connectionsBasePath = `/ns/${this.state.currentNamespace}/connections`;
      if (!match) {
        if (location.pathname.startsWith(dataprepBasePath) || location.pathname.startsWith(connectionsBasePath)) {
          return true;
        }
        return false;
      }
      if (match.isExact) { return true; }

      if (location.pathname.startsWith(dataprepBasePath) || location.pathname.startsWith(connectionsBasePath)) {
        return true;
      }
      return false;
    };

    return (
      <div className="global-navbar">
        <div
          className="global-navbar-toggler float-xs-right btn"
          onClick={this.toggleNavbar.bind(this)}
        >
          {
            !this.state.toggleNavbar ?
              <i className="fa fa-bars fa-2x"></i>
            :
              <i className="fa fa-times fa-2x"></i>
          }
        </div>
        <div className="brand-section">
          {
            !this.props.nativeLink ?
              <NavLink
                to={`/ns/${this.state.currentNamespace}`}
              >
                <img src="/cdap_assets/img/company_logo.png" />
              </NavLink>
            :
              <a href={window.getAbsUIUrl({namespaceId: this.state.currentNamespace})}>
                <img src="/cdap_assets/img/company_logo.png" />
              </a>
          }
        </div>
        <ul className="navbar-list-section">
          <li>
            {
              !this.props.nativeLink ?
                <NavLink
                  to={`/ns/${this.state.currentNamespace}`}
                  isActive={isCDAPActive}
                >
                  {T.translate('features.Navbar.overviewLabel')}
                </NavLink>
              :
                <a href={overviewUrl}>
                  {T.translate('features.Navbar.overviewLabel')}
                </a>
            }
          </li>
          <li>
            {
              !this.props.nativeLink ?
                (
                  <NavLink
                    to={`/ns/${this.state.currentNamespace}/dataprep`}
                    isActive={isDataPrepActive}
                  >
                    {T.translate('features.Navbar.dataprepLabel')}
                  </NavLink>
                )
              :
                (
                  <a href={dataprepUrl}>
                    {T.translate('features.Navbar.dataprepLabel')}
                  </a>
                )
            }
          </li>
          <li>
            <a
              href={pipelinesListUrl}
              className={classnames({'active': isPipelinesViewActive})}
            >
              {T.translate('features.Navbar.pipelinesLabel')}
            </a>
          </li>
          <li>
            <MetadataDropdown />
          </li>
        </ul>
        <div className={classnames("global-navbar-collapse", {
            'minimized': this.state.toggleNavbar
          })}>
          <div className="navbar-right-section">
            <ul>
              <li className="with-shadow">
                <CaskMarketButton>
                  <span className="fa icon-CaskMarket"></span>
                  <span>{T.translate('commons.market')}</span>
                </CaskMarketButton>
              </li>
              <li
                id="header-namespace-dropdown"
                className="with-shadow namespace-dropdown-holder">
                {
                  !this.props.nativeLink ?
                    <NamespaceDropdown />
                  :
                    <NamespaceDropdown tag="a"/>
                }
              </li>
              <li className="with-shadow cdap-menu clearfix">
                <ProductDropdown
                  nativeLink={this.props.nativeLink}
                />
              </li>
            </ul>
          </div>
        </div>
      </div>
    );
  }
}

Header.defaultProps = {
  nativeLink: false
};

Header.propTypes = {
  nativeLink: PropTypes.bool
};
