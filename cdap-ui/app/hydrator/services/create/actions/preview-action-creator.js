/*
 * Copyright © 2016 Cask Data, Inc.
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

class HydratorPlusPlusPreviewActions {
  constructor (PREVIEWSTORE_ACTIONS) {
    'ngInject';
    this.previewActions = PREVIEWSTORE_ACTIONS;
  }

  togglePreviewMode (isPreviewModeEnabled) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.TOGGLE_PREVIEW_MODE,
        payload: {isPreviewModeEnabled}
      });
    };
  }

  setPreviewStartTime (startTime) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_PREVIEW_START_TIME,
        payload: {startTime}
      });
    };
  }

  setPreviewId (previewId) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_PREVIEW_ID,
        payload: {previewId}
      });
    };
  }

  setMacros (macros) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_MACROS,
        payload: {macros}
      });
    };
  }

  setUserRuntimeArguments (userRuntimeArguments) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_USER_RUNTIME_ARGUMENTS,
        payload: {userRuntimeArguments}
      });
    };
  }

  setMacrosAndUserRuntimeArgs (macros, userRuntimeArguments) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_MACROS,
        payload: {macros}
      });
      dispatch({
        type: this.previewActions.SET_USER_RUNTIME_ARGUMENTS,
        payload: {userRuntimeArguments}
      });
    };
  }

  setTimeoutInMinutes (timeoutInMinutes) {
    return (dispatch) => {
      dispatch({
        type: this.previewActions.SET_TIMEOUT_IN_MINUTES,
        payload: {timeoutInMinutes}
      });
    };
  }
}

angular.module(`${PKG.name}.feature.hydrator`)
  .service('HydratorPlusPlusPreviewActions', HydratorPlusPlusPreviewActions);
