/*
 * Copyright 2015 Cask Data, Inc.
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

package co.cask.cdap.data2.dataset2.lib.cube;

import co.cask.cdap.api.dataset.lib.cube.Cube;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.InMemoryMetricsTable;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.InMemoryTableService;
import co.cask.cdap.data2.dataset2.lib.timeseries.EntityTable;
import co.cask.cdap.data2.dataset2.lib.timeseries.FactTable;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 *
 */
public class DefaultCubeTest extends AbstractCubeTest {

  @Override
  protected Cube getCube(final String name, int[] resolutions, Map<String, ? extends Aggregation> aggregations) {
    FactTableSupplier supplier = new FactTableSupplier() {
      @Override
      public FactTable get(int resolution, int rollTime) {
        String entityTableName = "EntityTable-" + name;
        InMemoryTableService.create(entityTableName);
        String dataTableName = "DataTable-" + name + "-" + resolution;
        InMemoryTableService.create(dataTableName);
        return new FactTable(new InMemoryMetricsTable(dataTableName),
                             new EntityTable(new InMemoryMetricsTable(entityTableName)),
                             resolution, rollTime);

      }
    };

    return new DefaultCube(resolutions, supplier, aggregations, ImmutableMap.<String, AggregationAlias>of());
  }
}
