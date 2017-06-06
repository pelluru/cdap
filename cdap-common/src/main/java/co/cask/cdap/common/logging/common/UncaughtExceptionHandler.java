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

package co.cask.cdap.common.logging.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UncaughtExceptionHandler to log uncaught exceptions
 */
public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(UncaughtExceptionHandler.class);

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    // If the Throwable is an Error, the system most likely is in unusable state.
    // Hence we try our best effort to log the message first to at least get some information,
    // followed by logging the stacktrace (in OOM case, it may fail to get the stacktrace)
    if (e instanceof Error) {
      LOG.error("Uncaught error in thread {}, {}", t, e.toString());
      LOG.error("Stacktrace for uncaught error in thread {}", t, e);
      return;
    }

    StackTraceElement[] stackTrace = e.getStackTrace();
    if (stackTrace.length > 0) {
      Logger logger = LoggerFactory.getLogger(stackTrace[0].getClassName());
      logger.debug("Uncaught exception in thread {}", t, e);
    } else {
      LOG.debug("Uncaught exception in thread {}", t, e);
    }
  }
}
