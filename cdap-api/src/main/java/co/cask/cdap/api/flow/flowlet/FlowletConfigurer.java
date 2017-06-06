/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.api.flow.flowlet;

import co.cask.cdap.api.DatasetConfigurer;
import co.cask.cdap.api.Resources;

import java.util.Map;

/**
 * Configurer for configuring {@link Flowlet}.
 */
public interface FlowletConfigurer extends DatasetConfigurer {

  /**
   * Sets the name of the {@link Flowlet}.
   *
   * @param name name of the flowlet
   */
  void setName(String name);

  /**
   * Sets the description of the {@link Flowlet}.
   *
   * @param description description of the flowlet
   */
  void setDescription(String description);

  /**
   * Sets the resource requirements for the {@link Flowlet}.
   *
   * @param resources {@link Resources}
   */
  void setResources(Resources resources);

  /**
   * Sets the failure policy for the {@link Flowlet}.
   *
   * @param failurePolicy {@link FailurePolicy}
   */
  void setFailurePolicy(FailurePolicy failurePolicy);

  /**
   * Sets a map of properties that will be available through {@link FlowletSpecification} at runtime.
   *
   * @param properties properties
   */
  void setProperties(Map<String, String> properties);
}
