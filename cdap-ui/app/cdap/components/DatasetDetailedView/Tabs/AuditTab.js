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

import React, { PropTypes } from 'react';
import NamespaceStore from 'services/NamespaceStore';

export default function AuditTab({entity}) {
  let namespace = NamespaceStore.getState().selectedNamespace;
  let url = window.getTrackerUrl({
    stateName: 'tracker.detail.entity.audit',
    stateParams: {
      namespace,
      entityType: 'datasets',
      entityId: entity.id,
      iframe: true
    }
  });
  let encodedSource = encodeURIComponent(url);
  url += `&sourceUrl=${encodedSource}`;

  return (
    <div className="embed-responsive embed-responsive-16by9">
      <iframe
        src={url}
        frameBorder="0"
        width="100%"
        height="100%"
        className="embed-responsive-item"
      >
      </iframe>
    </div>
  );
}

AuditTab.propTypes = {
  entity: PropTypes.object
};
