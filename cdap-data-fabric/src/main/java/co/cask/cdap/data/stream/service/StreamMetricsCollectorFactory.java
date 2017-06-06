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

package co.cask.cdap.data.stream.service;

import co.cask.cdap.proto.id.StreamId;

/**
 * Factory for creating {@link StreamMetricsCollector}s.
 */
public interface StreamMetricsCollectorFactory {

  /**
   * Collector of metrics for a stream.
   */
  public interface StreamMetricsCollector {

    /**
     * Emit stream metrics.
     *
     * @param bytesWritten number of bytes written to the stream
     * @param eventsWritten number of events written to the stream
     */
    void emitMetrics(long bytesWritten, long eventsWritten);
  }

  /**
   * Create a {@link StreamMetricsCollector} for the given {@code streamId}.
   *
   * @param streamId stream name to create a collector for
   * @return a {@link StreamMetricsCollector} for the given {@code streamId}
   */
  StreamMetricsCollector createMetricsCollector(StreamId streamId);
}
