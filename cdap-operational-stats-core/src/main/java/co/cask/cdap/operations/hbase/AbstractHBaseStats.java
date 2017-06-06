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

package co.cask.cdap.operations.hbase;

import co.cask.cdap.operations.AbstractOperationalStats;
import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.conf.Configuration;

/**
 * Base class for HBase operational stats.
 */
public abstract class AbstractHBaseStats extends AbstractOperationalStats {
  @VisibleForTesting
  static final String SERVICE_NAME = "HBase";

  protected final Configuration conf;

  @VisibleForTesting
  AbstractHBaseStats(Configuration conf) {
    this.conf = conf;
  }

  @Override
  public String getServiceName() {
    return SERVICE_NAME;
  }
}
