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

import DataSourceConfigurer from 'services/datasource/DataSourceConfigurer';
import {apiCreator} from 'services/resource-helper';

let dataSrc = DataSourceConfigurer.getInstance();

const appPath = '/namespaces/:namespace/apps/dataprep';
const baseServicePath = `${appPath}/services/service`;
const basepath = `${baseServicePath}/methods/workspaces/:workspaceId`;
const connectionsPath = `${baseServicePath}/methods/connections`;

const MyDataPrepApi = {
  create: apiCreator(dataSrc, 'PUT', 'REQUEST', basepath),
  delete: apiCreator(dataSrc, 'DELETE', 'REQUEST', basepath),
  upload: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/upload`),
  execute: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/execute`),
  summary: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/summary`),
  getSchema: apiCreator(dataSrc, 'POST', 'REQUEST', `${basepath}/schema`),
  getUsage: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/usage`),
  getInfo: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/info`),
  getWorkspace: apiCreator(dataSrc, 'GET', 'REQUEST', `${basepath}`),
  getWorkspaceList: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/workspaces`),

  // WRANGLER SERVICE MANAGEMENT
  getApp: apiCreator(dataSrc, 'GET', 'REQUEST', `${appPath}`),
  startService: apiCreator(dataSrc, 'POST', 'REQUEST', `${baseServicePath}/start`),
  stopService: apiCreator(dataSrc, 'POST', 'REQUEST', `${baseServicePath}/stop`),
  pollServiceStatus: apiCreator(dataSrc, 'GET', 'POLL', `${baseServicePath}/status`),
  createApp: apiCreator(dataSrc, 'PUT', 'REQUEST', `${appPath}`),
  ping: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/usage`, { interval: 2000 }),

  // File System Browser
  explorer: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/explorer/fs`),
  readFile: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/explorer/fs/read`),
  getSpecification: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/explorer/fs/specification`),

  // Database Browser
  listTables: apiCreator(dataSrc, 'GET', 'REQUEST', `${connectionsPath}/:connectionId/tables`),
  readTable: apiCreator(dataSrc, 'GET', 'REQUEST', `${connectionsPath}/:connectionId/tables/:tableId/read`),
  getDatabaseSpecification: apiCreator(dataSrc, 'GET', 'REQUEST', `${connectionsPath}/:connectionId/tables/:tableId/specification`),

  // JDBC
  jdbcDrivers: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/jdbc/drivers`),
  jdbcAllowed: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/jdbc/allowed`),
  jdbcTestConnection: apiCreator(dataSrc, 'POST', 'REQUEST', `${baseServicePath}/methods/connections/jdbc/test`),
  getDatabaseList: apiCreator(dataSrc, 'POST', 'REQUEST', `${connectionsPath}/databases`),

  // Connections
  listConnections: apiCreator(dataSrc, 'GET', 'REQUEST', `${connectionsPath}`),
  createConnection: apiCreator(dataSrc, 'POST', 'REQUEST', `${connectionsPath}/create`),
  updateConnection: apiCreator(dataSrc, 'POST', 'REQUEST', `${connectionsPath}/:connectionId/update`),
  deleteConnection: apiCreator(dataSrc, 'DELETE', 'REQUEST', `${connectionsPath}/:connectionId`),
  getConnection: apiCreator(dataSrc, 'GET', 'REQUEST', `${connectionsPath}/:connectionId`),
  listDrivers: apiCreator(dataSrc, 'GET', 'REQUEST', `${baseServicePath}/methods/list/drivers`)

};

export default MyDataPrepApi;
