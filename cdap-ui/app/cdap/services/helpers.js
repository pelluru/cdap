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

import isObject from 'lodash/isObject';
import numeral from 'numeral';
import moment from 'moment';
import isNil from 'lodash/isNil';
import isEmpty from 'lodash/isEmpty';

/*
  Purpose: Query a json object or an array of json objects
  Return: Returns undefined if property is not defined(never set) and
          and a valid value (including null) if defined.
  Usage:
    var obj1 = [
      {
        p1: 'something',
        p2: {
          p21: 'angular',
          p22: 21,
          p23: {
            p231: 'ember',
            p232: null
          }
        },
        p3: 1296,
        p4: [1, 2, 3],
        p5: null
      },
      {
        p101: 'somethingelse'
      }
    ]
    1. query(obj1, 0, 'p1') => 'something'
    2. query(obj1, 0, 'p2', 'p22') => 21
    3. query(obj1, 0, 'p2', 'p32') => { p231: 'ember'}
    4. query(obj1, 0, 'notaproperty') => undefined
    5. query(obj1, 0, 'p2', 'p32', 'somethingelse') => undefined
    6. query(obj1, 1, 'p2', 'p32') => undefined
    7. query(obj1, 0, 'p2', 'p23', 'p232') => null
    8. query(obj1, 0, 'p5') => null
 */

function objectQuery(obj) {
  if (!isObject(obj)) {
    return null;
  }
  for (var i = 1; i < arguments.length; i++) {
    if (!isObject(obj)) {
      return undefined;
    }
    obj = obj[arguments[i]];
  }
  return obj;
}
export const HUMANREADABLESTORAGE = 'STORAGE';
export const HUMANREADABLESTORAGE_NODECIMAL = "NODECIMAL";

function humanReadableNumber(num, type) {
  if (typeof num !== 'number') {
    return num;
  }

  switch (type) {
    case HUMANREADABLESTORAGE:
      return convertBytesToHumanReadable(num);
    case HUMANREADABLESTORAGE_NODECIMAL:
      return convertBytesToHumanReadable(num, HUMANREADABLESTORAGE_NODECIMAL);
    default:
      return numeral(num).format('0,0');
  }

}
function humanReadableDate(date, isMilliseconds) {
  const format = 'MM-DD-YYYY HH:mm:ss A';
  if (isMilliseconds) {
    return moment(date).format(format);
  }
  return (moment(date * 1000)).format(format);
}

function contructUrl ({path}) {
  return [
    window.CDAP_CONFIG.sslEnabled? 'https://': 'http://',
    window.CDAP_CONFIG.cdap.routerServerUrl,
    ':',
    window.CDAP_CONFIG.sslEnabled? window.CDAP_CONFIG.cdap.routerSSLServerPort: window.CDAP_CONFIG.cdap.routerServerPort,
    '/v3',
    path
  ].join('');
}


function convertBytesToHumanReadable(bytes, type, includeSpace) {
  if (!bytes || typeof bytes !== 'number') {
    return bytes;
  }
  let format = includeSpace ? '0.00 b' : '0.00b';

  if (type === HUMANREADABLESTORAGE_NODECIMAL) {
    format = includeSpace ? '0 b' : '0b';
  }

  return numeral(bytes).format(format);
}

function isDescendant(parent, child) {
  var node = child;
  while (node != null) {
    if (node == parent) {
      return true;
    }
    node = node.parentNode;
  }
  return false;
}

function getArtifactNameAndVersion (nameWithVersion) {
  // core-plugins-3.4.0-SNAPSHOT.jar
  // extracts version from the jar file name. We then get the name of the artifact (that is from the beginning up to version beginning)
  // Fixed it to use a suffix pattern. Added `\\-` to detect versions from names such as `redshifttos3-action-plugin-1.0.0.json`
  if (isNil(nameWithVersion) || isEmpty(nameWithVersion)) {
    return {name: nameWithVersion, version: undefined};
  }
  let regExpRule = new RegExp('\\-(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[.\\-](.*))?$');
  let version = regExpRule.exec(nameWithVersion);
  if (!version) {
    return {name: nameWithVersion, version: undefined};
  }
  version = version[0].slice(1);
  let name = nameWithVersion.substr(0, nameWithVersion.indexOf(version) -1);
  return { version, name };
}


function insertAt(arr, index, element) {
  return [
    ...arr.slice(0, index + 1),
    element,
    ...arr.slice(index + 1, arr.length)
  ];
}

function removeAt(arr, index) {
  return [
    ...arr.slice(0, index),
    ...arr.slice(index + 1, arr.length)
  ];
}

function getIcon(entity) {
  switch (entity) {
    case 'application':
    case 'app':
      return 'icon-fist';
    case 'datasetinstance':
    case 'dataset':
      return 'icon-datasets';
    case 'stream':
      return 'icon-streams';
    default:
      return 'fa-exclamation-triangle';
  }
}

const defaultEventObject = {
  stopPropagation: () => {},
  nativeEvent: {
    stopImmediatePropagation: () => {}
  },
  preventDefault: () => {}
};
function preventPropagation(e = defaultEventObject) {
  e.stopPropagation();
  e.nativeEvent.stopImmediatePropagation();
  e.preventDefault();
}

const defaultAction = {
  action : '',
  payload : {}
};

export {
  objectQuery,
  convertBytesToHumanReadable,
  humanReadableNumber,
  isDescendant,
  getArtifactNameAndVersion,
  insertAt,
  removeAt,
  humanReadableDate,
  contructUrl,
  getIcon,
  preventPropagation,
  defaultAction
};
