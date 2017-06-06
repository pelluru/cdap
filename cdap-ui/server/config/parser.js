/*
 * Copyright © 2015 Cask Data, Inc.
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

/*global require, module, process */

module.exports = {
  extractConfig: extractConfig,
  extractUISettings: extractUISettings
};

var promise = require('q'),
    spawn = require('child_process').spawn,
    StringDecoder = require('string_decoder').StringDecoder,
    decoder = new StringDecoder('utf8'),
    log4js = require('log4js'),
    cache = {},
    path,
    buffer = '';

var log = log4js.getLogger('default');

function extractUISettings() {
  try {
    if (require.resolve('./ui-settings.json')) {
      return require('./ui-settings.json') || {};
    }
  } catch(e) {
    log.info('Unable to find UI settings json file.');
    return {};
  }
}

/*
 *  Extracts the config
 *  @returns {promise}
 */

function extractConfig(param) {
  var deferred = promise.defer(),
      tool;
  param = param || 'cdap';

  if (cache[param]) {
    deferred.resolve(cache[param]);
    return deferred.promise;
  }

  if (process.env.NODE_ENV === 'production') {
    buffer = '';
    tool = spawn(__dirname + '/../../bin/cdap', ['config-tool', '--'+param]);
    tool.stderr.on('data', configReadFail.bind(this));
    tool.stdout.on('data', configRead.bind(this));
    tool.stdout.on('end', onConfigReadEnd.bind(this, deferred, param));
  } else {
    try {
      path = getConfigPath(param);
      if (path && path.length) {
        path = path.replace(/\"/g, '');
        cache[param] = require(path);
      } else {
        throw 'No configuration JSON provided.(No "cConf" and "sConf" commandline arguments passed)';
      }
    } catch(e) {
      log.warn(e);
      // Indicates the backend is not running in local environment and that we want only the
      // UI to be running. This is here for convenience.
      log.warn('Using development configuration for "' + param + '"');
      cache[param] = require('./development/'+param+'.json');
    }

    deferred.resolve(cache[param]);
  }
  return deferred.promise;
}

function getConfigPath(param) {
  var configName = (param ==='security'? 'sConf': 'cConf');
  // If cConf and sConf are not provided (Starting node server
  // from console) default to development config.
  if (process.argv.length < 3) {
    return null;
  }
  var args = process.argv.slice(2),
      value = '',
      i;
  for (i=0; i<args.length; i++) {
    if (args[i].indexOf(configName) !== -1) {
      value = args[i].split('=');
      if (value.length > 1) {
        value = value[1];
      }
      break;
    }
  }
  return value;
}

function onConfigReadEnd (deferred, param) {
   cache[param] = JSON.parse(buffer);
   deferred.resolve(cache[param]);
}

function configRead (data) {
  var textChunk = decoder.write(data);
  if (textChunk) {
    buffer += textChunk;
  }
}

function configReadFail (data) {
  var textChunk = decoder.write(data);
  if (textChunk) {
    log.error(textChunk);
  }
}
