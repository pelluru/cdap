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

package co.cask.cdap.data2.dataset2;

import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.AbstractDataset;
import co.cask.cdap.api.dataset.module.EmbeddedDataset;

/**
 *
 */
public class DoubleWrappedKVTable extends AbstractDataset implements KeyValueTable {
  private final SimpleKVTable table;

  public DoubleWrappedKVTable(DatasetSpecification spec,
                              @EmbeddedDataset("data") SimpleKVTable table) {
    super(spec.getName(), table);
    this.table = table;
  }

  public void put(String key, String value) throws Exception {
    table.put(key, value);
  }

  public String get(String key) throws Exception {
    return table.get(key);
  }
}
