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
package co.cask.cdap.examples.wordcount;

import co.cask.cdap.api.annotation.ReadOnly;
import co.cask.cdap.api.annotation.WriteOnly;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.AbstractDataset;
import co.cask.cdap.api.dataset.module.EmbeddedDataset;
import co.cask.cdap.api.dataset.table.Get;
import co.cask.cdap.api.dataset.table.Table;

/**
 * Counts the number of unique entries seen given any number of entries.
 */
public class UniqueCountTable extends AbstractDataset {

  /**
   * Row and column names used for storing the unique count.
   */
  private static final byte[] UNIQUE_COUNT = Bytes.toBytes("unique");

  /**
   * Column name used for storing count of each entry.
   */
  private static final byte[] ENTRY_COUNT = Bytes.toBytes("count");
  private Table uniqueCountTable;
  private Table entryCountTable;

  public UniqueCountTable(DatasetSpecification spec,
                          @EmbeddedDataset("unique") Table uniqueCountTable,
                          @EmbeddedDataset("entry") Table entryCountTable) {
    super(spec.getName(), uniqueCountTable, entryCountTable);
    this.uniqueCountTable = uniqueCountTable;
    this.entryCountTable = entryCountTable;
  }

  /**
   * Returns the current unique count.
   *
   * @return current number of unique entries
   */
  @ReadOnly
  public Long readUniqueCount() {
    return uniqueCountTable.get(new Get(UNIQUE_COUNT, UNIQUE_COUNT)).getLong(UNIQUE_COUNT, 0);
  }

  /**
   * Adds the specified entry to the Table and augments the specified tuple with
   * a special field that will be used in the downstream Flowlet that this tuple is
   * sent to.
   *
   * Continuously add entries into the Table using this method, pass the tuple
   * to another downstream Flowlet, and in the second Flowlet pass the tuple to
   * the {@link #updateUniqueCount(String)}.
   *
   * @param entry entry to add
   */
  @WriteOnly
  public void updateUniqueCount(String entry) {
    long newCount = this.entryCountTable.incrementAndGet(Bytes.toBytes(entry), ENTRY_COUNT, 1L);
    if (newCount == 1L) {
      this.uniqueCountTable.increment(UNIQUE_COUNT, UNIQUE_COUNT, 1L);
    }
  }
}
