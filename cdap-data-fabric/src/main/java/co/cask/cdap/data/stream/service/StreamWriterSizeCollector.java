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
import com.google.common.util.concurrent.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keep track of the sizes of the files written by one {@link StreamHandler}.
 */
public interface StreamWriterSizeCollector {

  // TODO have one implementation of this

  /**
   * @return the streamSizes tracked by this StreamWriterSizeCollector.
   */
  Map<StreamId, AtomicLong> getStreamSizes();

  /**
   * Get the total amount of bytes collected for the stream {@code streamId} so far.
   *
   * @param streamId stream Id to get the total amount of data collected for
   * @return the total amount of bytes collected for the stream {@code streamId} so far
   */
  long getTotalCollected(StreamId streamId);

  /**
   * Called to notify this manager that {@code dataSize} bytes of data has been ingested by the stream
   * {@code streamId} using the stream handler from which this code is executed. The {@code dataSize}
   * is an incremental size.
   *
   * @param streamId Id of the stream that ingested data.
   * @param dataSize amount of data ingested in bytes.
   */
  void received(StreamId streamId, long dataSize);
}
