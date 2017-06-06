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

package co.cask.cdap.logging.meta;

import java.util.Map;
import java.util.Set;

/**
 * Manages reading/writing of checkpoint information for a topic and partition.
 */
public interface CheckpointManager {

  /**
   * Persists the given map of {@link Checkpoint}s.
   */
  void saveCheckpoints(Map<Integer, ? extends Checkpoint> checkpoints) throws Exception;

  /**
   * Reads the set of {@link Checkpoint}s for the given set of partitions. If there is no checkpoint for the partition,
   * a {@link Checkpoint} with both {@link Checkpoint#getMaxEventTime()} and
   * {@link Checkpoint#getNextOffset()} returning {@code -1} will be used.
   */
  Map<Integer, Checkpoint> getCheckpoint(Set<Integer> partitions) throws Exception;

  /**
   * Reads the {@link Checkpoint} for the given partition. If there is no checkpoint for the partition,
   * a {@link Checkpoint} will be returned with both {@link Checkpoint#getMaxEventTime()} and
   * {@link Checkpoint#getNextOffset()} returning {@code -1}.
   */
  Checkpoint getCheckpoint(int partition) throws Exception;
}
