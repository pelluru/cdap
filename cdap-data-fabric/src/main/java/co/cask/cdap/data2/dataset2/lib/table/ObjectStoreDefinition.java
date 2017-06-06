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

package co.cask.cdap.data2.dataset2.lib.table;

import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.IncompatibleUpdateException;
import co.cask.cdap.api.dataset.Reconfigurable;
import co.cask.cdap.api.dataset.lib.AbstractDatasetDefinition;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.dataset.lib.ObjectStore;
import co.cask.cdap.internal.io.SchemaTypeAdapter;
import co.cask.cdap.internal.io.TypeRepresentation;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * DatasetDefinition for {@link ObjectStoreDataset}.
 */
public class ObjectStoreDefinition
  extends AbstractDatasetDefinition<ObjectStore, DatasetAdmin>
  implements Reconfigurable {

  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(Schema.class, new SchemaTypeAdapter())
    .create();

  private final DatasetDefinition<? extends KeyValueTable, ?> tableDef;

  public ObjectStoreDefinition(String name, DatasetDefinition<? extends KeyValueTable, ?> keyValueDef) {
    super(name);
    Preconditions.checkArgument(keyValueDef != null, "KeyValueTable definition is required");
    this.tableDef = keyValueDef;
  }

  @Override
  public DatasetSpecification configure(String instanceName, DatasetProperties properties) {
    return DatasetSpecification.builder(instanceName, getName())
      .properties(properties.getProperties())
      .datasets(tableDef.configure("objects", checkAndRemoveSchema(properties)))
      .build();
  }

  @Override
  public DatasetSpecification reconfigure(String instanceName,
                                          DatasetProperties newProperties,
                                          DatasetSpecification currentSpec) throws IncompatibleUpdateException {
    // TODO (CDAP-6268): validate schema compatibility
    return DatasetSpecification.builder(instanceName, getName())
      .properties(newProperties.getProperties())
      .datasets(AbstractDatasetDefinition.reconfigure(tableDef, "objects", checkAndRemoveSchema(newProperties),
                                                      currentSpec.getSpecification("objects")))
      .build();
  }

  /**
   * strip schema from the properties sent to the underlying table, since ObjectStore allows schema that tables do not
   */
  private DatasetProperties checkAndRemoveSchema(DatasetProperties properties) {
    Preconditions.checkArgument(properties.getProperties().containsKey("type"));
    Preconditions.checkArgument(properties.getProperties().containsKey("schema"));
    Map<String, String> tableProperties = Maps.newHashMap(properties.getProperties());
    tableProperties.remove("type");
    tableProperties.remove("schema");
    return DatasetProperties.of(tableProperties);
  }

  @Override
  public DatasetAdmin getAdmin(DatasetContext datasetContext, DatasetSpecification spec,
                               ClassLoader classLoader) throws IOException {
    return tableDef.getAdmin(datasetContext, spec.getSpecification("objects"), classLoader);
  }

  @Override
  public ObjectStoreDataset<?> getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                                          Map<String, String> arguments, ClassLoader classLoader) throws IOException {
    DatasetSpecification kvTableSpec = spec.getSpecification("objects");
    KeyValueTable table = tableDef.getDataset(datasetContext, kvTableSpec, arguments, classLoader);

    TypeRepresentation typeRep = GSON.fromJson(spec.getProperty("type"), TypeRepresentation.class);
    Schema schema = GSON.fromJson(spec.getProperty("schema"), Schema.class);
    return new ObjectStoreDataset(spec.getName(), table, typeRep, schema, classLoader);
  }
}
