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

package co.cask.cdap.common.logging;

import org.slf4j.Logger;

/**
 * Utility class to provide methods to alter slf4j {@link Logger} behavior.
 */
public final class Loggers {

  /**
   * Creates a new {@link Logger} that only log when the log is accepted by the {@link LogSampler}.
   *
   * @param logger the {@link Logger} to use for emitting logs
   * @param sampler a {@link LogSampler} to decide if needs to emit the log or not
   * @return a new {@link Logger}.
   */
  public static Logger sampling(Logger logger, LogSampler sampler) {
    return new LocationAwareWrapperLogger(logger, sampler);
  }

  public static Logger mdcWrapper(Logger logger, String mdcKey, String mdcValue) {
    return new LocationAwareWrapperLogger(logger, mdcKey, mdcValue);
  }

  private Loggers() {
    // no-op
  }
}
