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

package co.cask.cdap.internal.app.runtime.flow;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Represents an event with partition hashes that needs to be emitted by the
 * {@link co.cask.cdap.api.flow.flowlet.Flowlet}.
 * @param <T> type of event
 */
public class DataObject<T> {
  private final T data;
  private final Map<String, Object> partitions;

  /**
   * Simple constructor with only event and no partition hashes.
   * @param data event
   */
  public DataObject(T data) {
    this.data = data;
    this.partitions = ImmutableMap.of();
  }

  /**
   * Constructor with an event and a set of partition hashes.
   * @param data event
   * @param partitions mapping from partition key to object, which the {@link Object#hashCode()}
   *                   of the object value would be triggered to compute the actual partition value.
   */
  public DataObject(T data, Map<String, Object> partitions) {
    this.data = data;
    this.partitions = partitions;
  }

  /**
   * Constructor with an event and a single partition hash.
   * @param data event
   * @param partitionKey partition key
   * @param partitionValue an object, whose {@link Object#hashCode()} would be triggered
   *                       to compute the actual partition value
   */
  public DataObject(T data, String partitionKey, Object partitionValue) {
    this.data = data;
    this.partitions = ImmutableMap.of(partitionKey, partitionValue);
  }

  /**
   * Returns event.
   * @return event
   */
  public T getData() {
    return data;
  }

  /**
   * Returns partition map.
   * @return partition map
   */
  public Map<String, Object> getPartitions() {
    return partitions;
  }
}
