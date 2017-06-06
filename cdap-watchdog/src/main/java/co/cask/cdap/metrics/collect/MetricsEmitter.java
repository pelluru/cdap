/*
 * Copyright © 2014-2016 Cask Data, Inc.
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
package co.cask.cdap.metrics.collect;

import co.cask.cdap.api.metrics.MetricValue;

/**
 * A MetricsEmitter is a class that emits a {@link co.cask.cdap.api.metrics.MetricValue}.
 */
public interface MetricsEmitter {

  /**
   * Emits metric
   * @return A {@link co.cask.cdap.api.metrics.MetricValue} representing metrics for the given timestamp
   */
  MetricValue emit();
}
