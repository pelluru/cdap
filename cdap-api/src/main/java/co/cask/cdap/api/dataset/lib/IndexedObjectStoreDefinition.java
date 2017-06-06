/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.table.Table;

import java.io.IOException;
import java.util.Map;

/**
 * DatasetDefinition for {@link IndexedObjectStore}.
 */
@Beta
public class IndexedObjectStoreDefinition
  extends CompositeDatasetDefinition<IndexedObjectStore> {

  public IndexedObjectStoreDefinition(String name,
                                      DatasetDefinition<? extends Table, ?> tableDef,
                                      DatasetDefinition<? extends ObjectStore, ?> objectStoreDef) {
    super(name, "index", tableDef, "data", objectStoreDef);
  }

  @Override
  public IndexedObjectStore<?> getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                                          Map<String, String> arguments, ClassLoader classLoader) throws IOException {

    Table index = getDataset(datasetContext, "index", spec, arguments, classLoader);
    ObjectStore<?> objectStore = getDataset(datasetContext, "data", spec, arguments, classLoader);

    return new IndexedObjectStore<>(spec.getName(), objectStore, index);
  }
}
