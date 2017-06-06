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

package co.cask.cdap.api.dataset.lib;

import co.cask.cdap.api.mapreduce.MapReduceTaskContext;

/**
 * Responsible for dynamically determining a @{link PartitionKey}.
 * For each K, V pair, the getPartitionKey(K, V) method is called to determine a PartitionKey.
 *
 * @param <K> Type of key
 * @param <V> Type of value
 */
public abstract class DynamicPartitioner<K, V> {


  /**
   *  Initializes a DynamicPartitioner.
   *  <p>
   *    This method will be called only once per {@link DynamicPartitioner} instance. It is the first method call
   *    on that instance.
   *  </p>
   *  @param mapReduceTaskContext the mapReduceTaskContext for the task that this DynamicPartitioner is running in.
   *  Note that the hadoop context is not available on this MapReduceTaskContext.
   */
  public void initialize(MapReduceTaskContext<K, V> mapReduceTaskContext) {
    // do nothing by default
  }

  /**
   *  Destroys a DynamicPartitioner.
   *  <p>
   *    This method will be called only once per {@link DynamicPartitioner} instance. It is the last method call
   *    on that instance.
   *  </p>
   */
  public void destroy() {
    // do nothing by default
  }

  /**
   * Determine the PartitionKey for the key-value pair to be written to.
   *
   * @param key the key to be written
   * @param value the value to be written
   * @return the {@link PartitionKey} for the key-value pair to be written to.
   */
  public abstract PartitionKey getPartitionKey(K key, V value);
}
