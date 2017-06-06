/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.data2.dataset2.lib.partitioned;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.IncompatibleUpdateException;
import co.cask.cdap.api.dataset.Reconfigurable;
import co.cask.cdap.api.dataset.lib.AbstractDatasetDefinition;
import co.cask.cdap.api.dataset.lib.FileSet;
import co.cask.cdap.api.dataset.lib.FileSetArguments;
import co.cask.cdap.api.dataset.lib.IndexedTable;
import co.cask.cdap.api.dataset.lib.PartitionKey;
import co.cask.cdap.api.dataset.lib.PartitionedFileSet;
import co.cask.cdap.api.dataset.lib.PartitionedFileSetArguments;
import co.cask.cdap.api.dataset.lib.PartitionedFileSetProperties;
import co.cask.cdap.api.dataset.lib.Partitioning;
import co.cask.cdap.explore.client.ExploreFacade;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Defines the partitioned dataset type. At this time, the partitions are not managed by the
 * partitioned dataset, so all admin is simply on the partition table.
 * TODO rethink this
 */
public class PartitionedFileSetDefinition
  extends AbstractDatasetDefinition<PartitionedFileSet, DatasetAdmin>
  implements Reconfigurable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionedFileSetDefinition.class);

  protected static final String PARTITION_TABLE_NAME = "partitions";
  protected static final String FILESET_NAME = "files";

  private static final String INDEXED_COLS = Bytes.toString(PartitionedFileSetDataset.WRITE_PTR_COL) + ','
    + Bytes.toString(PartitionedFileSetDataset.CREATION_TIME_COL);

  protected final DatasetDefinition<? extends IndexedTable, ?> indexedTableDef;
  protected final DatasetDefinition<? extends FileSet, ?> filesetDef;

  @Inject
  private Injector injector;

  public PartitionedFileSetDefinition(String name,
                                      DatasetDefinition<? extends FileSet, ?> filesetDef,
                                      DatasetDefinition<? extends IndexedTable, ?> indexedTableDef) {
    super(name);
    Preconditions.checkArgument(indexedTableDef != null, "IndexedTable definition is required");
    Preconditions.checkArgument(filesetDef != null, "FileSet definition is required");
    this.filesetDef = filesetDef;
    this.indexedTableDef = indexedTableDef;
  }

  @Override
  public DatasetSpecification configure(String instanceName, DatasetProperties properties) {
    Partitioning partitioning = PartitionedFileSetProperties.getPartitioning(properties.getProperties());
    Preconditions.checkNotNull(partitioning, "Properties do not contain partitioning");
    // define the columns for indexing on the partitionsTable
    DatasetProperties indexedTableProperties = DatasetProperties.builder()
      .addAll(properties.getProperties())
      .add(IndexedTable.INDEX_COLUMNS_CONF_KEY, INDEXED_COLS)
      .build();
    return DatasetSpecification.builder(instanceName, getName())
      .properties(properties.getProperties())
      .datasets(filesetDef.configure(FILESET_NAME, properties),
                indexedTableDef.configure(PARTITION_TABLE_NAME, indexedTableProperties))
      .build();
  }

  @Override
  public DatasetSpecification reconfigure(String instanceName,
                                          DatasetProperties properties,
                                          DatasetSpecification currentSpec) throws IncompatibleUpdateException {

    // validate that the partitioning is not changing
    Partitioning oldPartitioning = PartitionedFileSetProperties.getPartitioning(currentSpec.getProperties());
    Partitioning newPartitioning = PartitionedFileSetProperties.getPartitioning(properties.getProperties());
    Preconditions.checkNotNull(oldPartitioning, "Existing dataset has no partitioning");
    Preconditions.checkNotNull(newPartitioning, "New properties do not contain partitioning");
    if (!Iterators.elementsEqual(oldPartitioning.getFields().entrySet().iterator(),
                                 newPartitioning.getFields().entrySet().iterator())) {
      throw new IncompatibleUpdateException(String.format(
        "Partitioning cannot be changed. Existing: %s, new: %s", oldPartitioning, newPartitioning));
    }

    // define the columns for indexing on the partitionsTable
    DatasetProperties indexedTableProperties = DatasetProperties.builder()
      .addAll(properties.getProperties())
      .add(IndexedTable.INDEX_COLUMNS_CONF_KEY, INDEXED_COLS)
      .build();
    return DatasetSpecification.builder(instanceName, getName())
      .properties(properties.getProperties())
      .datasets(AbstractDatasetDefinition.reconfigure(filesetDef, FILESET_NAME, properties,
                                                      currentSpec.getSpecification(FILESET_NAME)),
                AbstractDatasetDefinition.reconfigure(indexedTableDef, PARTITION_TABLE_NAME, indexedTableProperties,
                                                      currentSpec.getSpecification(PARTITION_TABLE_NAME)))
      .build();
  }

  @Override
  public DatasetAdmin getAdmin(DatasetContext datasetContext, DatasetSpecification spec,
                               ClassLoader classLoader) throws IOException {
    return new PartitionedFileSetAdmin(
      datasetContext, spec, getExploreProvider(),
      ImmutableMap.<String, DatasetAdmin>of(
        FILESET_NAME,
        filesetDef.getAdmin(datasetContext, spec.getSpecification(FILESET_NAME), classLoader),
        PARTITION_TABLE_NAME,
        indexedTableDef.getAdmin(datasetContext, spec.getSpecification(PARTITION_TABLE_NAME), classLoader)));
  }

  @Override
  public PartitionedFileSet getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                                       Map<String, String> arguments, ClassLoader classLoader) throws IOException {
    // properties must contain the partitioning
    Partitioning partitioning = PartitionedFileSetProperties.getPartitioning(spec.getProperties());

    // make any necessary updates to the arguments
    arguments = updateArgumentsIfNeeded(arguments, partitioning);

    FileSet fileset = filesetDef.getDataset(datasetContext, spec.getSpecification(FILESET_NAME),
                                            arguments, classLoader);
    IndexedTable table = indexedTableDef.getDataset(datasetContext, spec.getSpecification(PARTITION_TABLE_NAME),
                                                    arguments, classLoader);

    return new PartitionedFileSetDataset(datasetContext, spec.getName(), partitioning, fileset, table, spec, arguments,
                                         getExploreProvider());
  }

  // if the arguments do not contain an output location, generate one from the partition key (if present)
  protected static Map<String, String> updateArgumentsIfNeeded(Map<String, String> arguments,
                                                               Partitioning partitioning) {
    if (FileSetArguments.getOutputPath(arguments) == null) {
      PartitionKey key = PartitionedFileSetArguments.getOutputPartitionKey(arguments, partitioning);
      if (key != null) {
        arguments = Maps.newHashMap(arguments);
        FileSetArguments.setOutputPath(arguments, PartitionedFileSetDataset.getOutputPath(key, partitioning));
      } else if (PartitionedFileSetArguments.getDynamicPartitioner(arguments) != null) {
        // when using DynamicPartitioner, use the baseLocation of the fileSet as the output location
        FileSetArguments.setBaseOutputPath(arguments);
      }
    }
    return arguments;
  }

  protected Provider<ExploreFacade> getExploreProvider() {
    return new Provider<ExploreFacade>() {
      @Override
      public ExploreFacade get() {
        try {
          return injector.getInstance(ExploreFacade.class);
        } catch (Exception e) {
          // since explore is optional for this dataset, ignore but log it
          LOG.warn(String.format("Unable to get explore facade from injector for %s dataset.", getName()), e);
          return null;
        }
      }
    };
  }
}
