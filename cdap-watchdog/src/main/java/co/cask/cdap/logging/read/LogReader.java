/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.logging.read;

import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.logging.filter.Filter;

/**
 * Interface to read logs.
 */
public interface LogReader {
  /**
   * Read log events of a Flow or Map Reduce program after a given offset.
   * @param loggingContext context to look up log events.
   * @param readRange range for reading log events. Use {@link ReadRange#LATEST} to get the latest log events.
   * @param maxEvents max log events to return.
   * @param filter filter to select log events
   * @param callback callback to handle the log events.
   */
  void getLogNext(LoggingContext loggingContext, ReadRange readRange, int maxEvents, Filter filter,
                       Callback callback);

  /**
   * Read log events of a Flow or Map Reduce program before a given offset.
   * @param loggingContext context to look up log events.
   * @param readRange range for reading log events. Use {@link ReadRange#LATEST} to get the latest log events.
   * @param maxEvents max log events to return.
   * @param filter filter to select log events
   * @param callback callback to handle the log events.
   */
  void getLogPrev(LoggingContext loggingContext, ReadRange readRange, int maxEvents, Filter filter,
                       Callback callback);

  /**
    * Returns log events for a given LoggingContext between given times.
    * @param loggingContext context to look up log events.
    * @param fromTimeMs start time.
    * @param toTimeMs end time.
    * @param filter filter to select log events
    * @return CloseableIterator of log events
    */
  CloseableIterator<LogEvent> getLog(LoggingContext loggingContext, long fromTimeMs, long toTimeMs, Filter filter);
}
