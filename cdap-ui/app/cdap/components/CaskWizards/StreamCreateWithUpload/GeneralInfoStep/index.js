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
import React from 'react';
import CreateStreamWithUploadActions  from 'services/WizardStores/CreateStreamWithUpload/CreateStreamWithUploadActions';
import CreateStreamWithUploadStore from 'services/WizardStores/CreateStreamWithUpload/CreateStreamWithUploadStore';
import { Label, Form, FormGroup, Col, Input } from 'reactstrap';
import InputWithValidations from 'components/InputWithValidations';
import T from 'i18n-react';

import { connect, Provider } from 'react-redux';
require('./GeneralInfoStep.scss');
const mapStateToStreamNameProps = (state) => {
  return {
    value: state.general.name,
    type: 'text',
    placeholder: 'Stream Name'
  };
};
const mapStateToStreamDescritionProps = (state) => {
  return {
    value: state.general.description,
    type: 'textarea',
    rows: '7',
    placeholder: 'Description'
  };
};
const mapStateToStreamTTLProps = (state) => {
  return {
    value: state.general.ttl,
    placeholder: T.translate('features.Wizard.StreamCreate.Step1.ttl-placeholder')
  };
};

const mapDispatchToStreamNameProps = (dispatch) => {
  return {
    onChange: (e) => {
      dispatch({
        type: CreateStreamWithUploadActions.setName,
        payload: {name: e.target.value}
      });
    }
  };
};
const mapDispatchToStreamDescriptionProps = (dispatch) => {
  return {
    onChange: (e) => (dispatch({
      type: CreateStreamWithUploadActions.setDescription,
      payload: {description: e.target.value}
    }))
  };
};
const mapDispatchToToStreamTTL = (dispatch) => {
  return {
    onChange: (e) => {
      dispatch({
        type: CreateStreamWithUploadActions.setTTL,
        payload: {ttl: e.target.ttlValue}
      });
    }
  };
};

const InputStreamName = connect(
  mapStateToStreamNameProps,
  mapDispatchToStreamNameProps
)(InputWithValidations);
const InputStreamDescription = connect(
  mapStateToStreamDescritionProps,
  mapDispatchToStreamDescriptionProps
)(InputWithValidations);
const InputStreamTTL = connect(
  mapStateToStreamTTLProps,
  mapDispatchToToStreamTTL
)(Input);

export default function GeneralInfoStep() {
  return (
    <Provider store={CreateStreamWithUploadStore}>
      <Form
        className="form-horizontal general-info-step"
        onSubmit={(e) => {
          e.preventDefault();
          return false;
        }}
      >
        <FormGroup row>
          <Col xs="3">
            <Label className="control-label">{T.translate('commons.nameLabel')}</Label>
          </Col>
          <Col xs="7">
            <InputStreamName />
          </Col>
          <i className="fa fa-asterisk text-danger float-xs-left"/>
        </FormGroup>
        <FormGroup row>
          <Col xs="3">
            <Label className="control-label">{T.translate('commons.descriptionLabel')}</Label>
          </Col>
          <Col xs="7">
            <InputStreamDescription />
          </Col>
        </FormGroup>
        <FormGroup row>
          <Col sm="3">
            <Label className="control-label">{T.translate('features.Wizard.StreamCreate.Step1.ttllabel')} </Label>
          </Col>
          <Col sm="7">
            <InputStreamTTL />
          </Col>
        </FormGroup>
      </Form>
    </Provider>
  );
}
