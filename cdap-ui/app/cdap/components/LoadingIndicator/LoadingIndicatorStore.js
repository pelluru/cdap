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

import {combineReducers, createStore} from 'redux';

const defaultLoadingState = {
  status: 'BACKENDUP',
  message: '',
  subtitle: ''
};
const defaultAction = {
  action : '',
  payload : {}
};
const BACKENDSTATUS = {
  STATUSUPDATE: 'STATUSUPDATE',
  BACKENDUP: 'BACKENDUP',
  NODESERVERDOWN: 'NODESERVERDOWN',
  NODESERVERUP: 'NODESERVERUP',
  BACKENDDOWN: 'BACKENDDOWN'
};
const LOADINGSTATUS = {
  SHOWLOADING: 'SHOWLOADING',
  HIDELOADING: 'HIDELOADING'
};

const loading = (state = defaultLoadingState, action = defaultAction) => {
  switch (action.type) {
    case 'STATUSUPDATE':
      {
        let {status = LOADINGSTATUS.HIDELOADING, message = '', subtitle = ''} = action.payload;
        return Object.assign({}, state, {
          status,
          message,
          subtitle
        });}
    default:
      return state;
  }
};

const LoadingIndicatorStore = createStore(
  combineReducers({
    loading
  }),
  {loading: defaultLoadingState},
  window.__REDUX_DEVTOOLS_EXTENSION__ && window.__REDUX_DEVTOOLS_EXTENSION__()
);

export default LoadingIndicatorStore;
export {BACKENDSTATUS, LOADINGSTATUS};
