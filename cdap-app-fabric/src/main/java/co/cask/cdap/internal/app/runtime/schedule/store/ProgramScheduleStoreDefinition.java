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

package co.cask.cdap.internal.app.runtime.schedule.store;

import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.CompositeDatasetDefinition;
import co.cask.cdap.api.dataset.lib.IndexedTable;
import co.cask.cdap.api.dataset.table.ConflictDetection;
import co.cask.cdap.api.dataset.table.TableProperties;

import java.io.IOException;
import java.util.Map;

/**
 * Defines the program schedule store.
 */
public class ProgramScheduleStoreDefinition extends CompositeDatasetDefinition<ProgramScheduleStoreDataset> {

  public ProgramScheduleStoreDefinition(String name, DatasetDefinition<? extends IndexedTable, ?> tableDef) {
    super(name, ProgramScheduleStoreDataset.EMBEDDED_TABLE_NAME, tableDef);
  }

  @Override
  public DatasetSpecification configure(String name, DatasetProperties properties) {
    TableProperties.Builder indexProps = TableProperties.builder();
    indexProps.addAll(properties.getProperties());
    indexProps.add(IndexedTable.INDEX_COLUMNS_CONF_KEY, ProgramScheduleStoreDataset.INDEX_COLUMNS);
    indexProps.setConflictDetection(ConflictDetection.COLUMN);
    DatasetSpecification indexSpec = getDelegate(ProgramScheduleStoreDataset.EMBEDDED_TABLE_NAME)
      .configure(ProgramScheduleStoreDataset.EMBEDDED_TABLE_NAME, indexProps.build());
    return DatasetSpecification.builder(name, getName()).datasets(indexSpec).build();
  }

  @Override
  public ProgramScheduleStoreDataset getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                                                Map<String, String> arguments, ClassLoader classLoader)
    throws IOException {
    IndexedTable table = getDataset(
      datasetContext, ProgramScheduleStoreDataset.EMBEDDED_TABLE_NAME, spec, arguments, classLoader);
    return new ProgramScheduleStoreDataset(spec, table);
  }
}
