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

import {apiCreator} from 'services/resource-helper';
import DataSourceConfigurer from 'services/datasource/DataSourceConfigurer';
let dataSrc = DataSourceConfigurer.getInstance();
let basepath = '/namespaces/:namespace/:entityType/:entityId/metadata';

export const MyMetadataApi = {
  getMetadata: apiCreator(dataSrc, 'GET', 'REQUEST', basepath),
  getProperties: apiCreator(dataSrc, 'GET', 'REQUEST', `${basepath}/properties`),
  addProperties: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/properties`),
  deleteProperty: apiCreator(dataSrc, 'DELETE', 'REQUEST', `${basepath}/properties/:key`),
  getTags: apiCreator(dataSrc, 'GET', 'REQUEST', `${basepath}/tags`),
  addTags: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/tags`),
  deleteTags: apiCreator(dataSrc, 'DELETE', 'REQUEST', `${basepath}/tags/:key`)
};
