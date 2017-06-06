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

import co.cask.cdap.api.ProgramLifecycle;
import co.cask.cdap.api.annotation.Beta;

/**
 * Defines an interface for the Spark job.
 */
@Beta
public interface Spark {
  /**
   * Configures a {@link Spark} job using the given {@link SparkConfigurer}.
   */
  void configure(SparkConfigurer configurer);

  /**
   * Invoked before starting a Spark job.
   *
   * @param context job execution context
   * @throws Exception if there's an error during this method invocation
   * @deprecated Deprecated as of 3.5.0. Please use {@link ProgramLifecycle#initialize} instead, to initialize
   * the Spark program.
   */
  @Deprecated
  void beforeSubmit(SparkClientContext context) throws Exception;

  /**
   * Invoked after a Spark job finishes. Will not be called if: Job failed to start.
   *
   * @param succeeded defines the result of job execution: true if job succeeded, false otherwise
   * @param context   job execution context
   * @throws Exception if there's an error during this method invocation.
   * @deprecated Deprecated as of 3.5.0. Please use {@link ProgramLifecycle#destroy} instead to execute the code once
   * Spark program is completed either successfully or on failure.
   */
  @Deprecated
  void onFinish(boolean succeeded, SparkClientContext context) throws Exception;

}
