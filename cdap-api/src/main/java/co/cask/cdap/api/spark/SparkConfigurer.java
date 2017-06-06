/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.api.spark;

import co.cask.cdap.api.DatasetConfigurer;
import co.cask.cdap.api.ProgramConfigurer;
import co.cask.cdap.api.ProgramLifecycle;
import co.cask.cdap.api.Resources;
import co.cask.cdap.api.RuntimeContext;
import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.plugin.PluginConfigurer;

/**
 * Configurer for configuring {@link Spark}.
 */
@Beta
public interface SparkConfigurer extends ProgramConfigurer, DatasetConfigurer, PluginConfigurer {

  /**
   * Sets the Spark job main class name in specification. The main method of this class will be called to run the
   * Spark job
   *
   * @param className the fully qualified name of class containing the main method
   */
  void setMainClassName(String className);

  /**
   * Sets the resources requirement for the Spark client process. It is the process where the
   * {@link ProgramLifecycle#initialize(RuntimeContext)} and {@link ProgramLifecycle#destroy()} methods get executed.
   */
  void setClientResources(Resources resources);

  /**
   * Sets the resources requirement for the Spark driver process.
   */
  void setDriverResources(Resources resources);

  /**
   * Sets the resources requirement for the Spark executor processes.
   */
  void setExecutorResources(Resources resources);
}
