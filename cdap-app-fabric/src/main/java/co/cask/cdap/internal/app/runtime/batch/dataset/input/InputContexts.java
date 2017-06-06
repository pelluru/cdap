/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.batch.dataset.input;

import co.cask.cdap.api.data.batch.InputContext;
import co.cask.cdap.data2.dataset2.lib.partitioned.PartitionedFileSetDataset;

/**
 * Utility class that helps determine the {@link InputContext} to be used.
 */
public final class InputContexts {

  private InputContexts() { }

  /**
   * @param multiInputTaggedSplit the split given to this mapper task
   * @return an {@link InputContext} representing the input that this mapper task is processing
   */
  public static InputContext create(MultiInputTaggedSplit multiInputTaggedSplit) {
    String mappingString = multiInputTaggedSplit.getConf().get(PartitionedFileSetDataset.PATH_TO_PARTITIONING_MAPPING);
    if (mappingString != null) {
      return new BasicPartitionedFileSetInputContext(multiInputTaggedSplit);
    }
    return new BasicInputContext(multiInputTaggedSplit.getName());
  }
}
