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

package co.cask.cdap.common.twill;

import org.apache.twill.api.ClassAcceptor;

import java.net.URL;

/**
 * Exclude hadoop classes
 */
public class HadoopClassExcluder extends ClassAcceptor {

  @Override
  public boolean accept(String className, URL classUrl, URL classPathUrl) {
    // exclude hadoop but not hbase and hive packages
    if (className.startsWith("org.apache.hadoop.")) {
      if (className.startsWith("org.apache.hadoop.hive.")) {
        return true;
      } else if (className.startsWith("org.apache.hadoop.hbase.")) {
        // exclude tracing dependencies of classes that have dependencies on commons-logging implementation classes
        // so that commons-logging jar is not packaged (this is required so that slf4j is used for log collection)
        return !(className.startsWith("org.apache.hadoop.hbase.http.log.LogLevel")
          || className.startsWith("org.apache.hadoop.hbase.http.HttpRequestLog"));
      } else {
        return false;
      }
    }
    return true;
  }
}
