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

package co.cask.cdap.etl.mock.batch;

import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.batch.Input;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.table.Row;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.api.plugin.PluginClass;
import co.cask.cdap.api.plugin.PluginConfig;
import co.cask.cdap.api.plugin.PluginPropertyField;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.batch.BatchRuntimeContext;
import co.cask.cdap.etl.api.batch.BatchSource;
import co.cask.cdap.etl.api.batch.BatchSourceContext;
import co.cask.cdap.etl.proto.v2.ETLPlugin;
import co.cask.cdap.format.StructuredRecordStringConverter;
import co.cask.cdap.test.DataSetManager;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Mock source that can be used to write a list of records in a Table and reads them out in a pipeline run.
 */
@Plugin(type = BatchSource.PLUGIN_TYPE)
@Name("Mock")
public class MockSource extends BatchSource<byte[], Row, StructuredRecord> {
  public static final PluginClass PLUGIN_CLASS = getPluginClass();
  private static final byte[] SCHEMA_COL = Bytes.toBytes("s");
  private static final byte[] RECORD_COL = Bytes.toBytes("r");
  private final Config config;

  public MockSource(Config config) {
    this.config = config;
  }

  /**
   * Config for the source.
   */
  public static class Config extends PluginConfig {
    private String tableName;

    @Nullable
    private String schema;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    super.configurePipeline(pipelineConfigurer);
    pipelineConfigurer.createDataset(config.tableName, Table.class);
    if (config.schema != null) {
      try {
        pipelineConfigurer.getStageConfigurer().setOutputSchema(Schema.parseJson(config.schema));
      } catch (IOException e) {
        throw new IllegalArgumentException("Could not parse schema " + config.schema, e);
      }
    }
  }

  @Override
  public void initialize(BatchRuntimeContext context) throws Exception {
    super.initialize(context);
    if (config.schema != null) {
      // should never happen, just done to test App correctness in unit tests
      Schema outputSchema = Schema.parseJson(config.schema);
      if (!outputSchema.equals(context.getOutputSchema())) {
        throw new IllegalStateException("Output schema does not match what was set at configure time.");
      }
    }
  }

  @Override
  public void transform(KeyValue<byte[], Row> input, Emitter<StructuredRecord> emitter) throws Exception {
    Schema schema = Schema.parseJson(input.getValue().getString(SCHEMA_COL));
    String recordStr = input.getValue().getString(RECORD_COL);
    emitter.emit(StructuredRecordStringConverter.fromJsonString(recordStr, schema));
  }

  @Override
  public void prepareRun(BatchSourceContext context) throws Exception {
    context.setInput(Input.ofDataset(config.tableName));
  }

  /**
   * Get the plugin config to be used in a pipeline config. If the source outputs records of the same schema,
   * {@link #getPlugin(String, Schema)} should be used instead, so that the source will set an output schema.
   *
   * @param tableName the table backing the mock source
   * @return the plugin config to be used in a pipeline config
   */
  public static ETLPlugin getPlugin(String tableName) {
    Map<String, String> properties = new HashMap<>();
    properties.put("tableName", tableName);
    return new ETLPlugin("Mock", BatchSource.PLUGIN_TYPE, properties, null);
  }

  /**
   * Get the plugin config to be used in a pipeline config. The source must only output records with the given schema.
   *
   * @param tableName the table backing the mock source
   * @param schema the schema of records output by this source
   * @return the plugin config to be used in a pipeline config
   */
  public static ETLPlugin getPlugin(String tableName, Schema schema) {
    Map<String, String> properties = new HashMap<>();
    properties.put("tableName", tableName);
    properties.put("schema", schema.toString());
    return new ETLPlugin("Mock", BatchSource.PLUGIN_TYPE, properties, null);
  }

  /**
   * Used to write the input records for the pipeline run. Should be called after the pipeline has been created.
   *
   * @param tableManager dataset manager used to write to the source dataset
   * @param records records that should be the input for the pipeline
   */
  public static void writeInput(DataSetManager<Table> tableManager,
                                Iterable<StructuredRecord> records) throws Exception {
    writeInput(tableManager, null, records);
  }

  /**
   * Used to write the input record with specified row key for the pipeline run.
   * Should be called after the pipeline has been created.
   *
   * @param tableManager dataset manager used to write to the source dataset
   * @param rowKey the row key of the table
   * @param record record that should be the input for the pipeline
   */
  public static void writeInput(DataSetManager<Table> tableManager, String rowKey,
                                StructuredRecord record) throws Exception {
    writeInput(tableManager, rowKey, ImmutableList.of(record));
  }

  private static void writeInput(DataSetManager<Table> tableManager, @Nullable String rowKey,
                                 Iterable<StructuredRecord> records) throws Exception {
    tableManager.flush();
    Table table = tableManager.get();
    // write each record as a separate row, with the serialized record as one column and schema as another
    // each rowkey will be a UUID.
    for (StructuredRecord record : records) {
      byte[] row = rowKey == null ? Bytes.toBytes(UUID.randomUUID()) : Bytes.toBytes(rowKey);
      table.put(row, SCHEMA_COL, Bytes.toBytes(record.getSchema().toString()));
      table.put(row, RECORD_COL, Bytes.toBytes(StructuredRecordStringConverter.toJsonString(record)));
    }
    tableManager.flush();
  }

  private static PluginClass getPluginClass() {
    Map<String, PluginPropertyField> properties = new HashMap<>();
    properties.put("tableName", new PluginPropertyField("tableName", "", "string", true, false));
    properties.put("schema", new PluginPropertyField("schema", "", "string", false, false));
    return new PluginClass(BatchSource.PLUGIN_TYPE, "Mock", "", MockSource.class.getName(), "config", properties);
  }
}
