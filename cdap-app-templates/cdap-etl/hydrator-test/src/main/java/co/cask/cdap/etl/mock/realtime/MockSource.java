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

package co.cask.cdap.etl.mock.realtime;

import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.plugin.PluginClass;
import co.cask.cdap.api.plugin.PluginConfig;
import co.cask.cdap.api.plugin.PluginPropertyField;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.realtime.RealtimeContext;
import co.cask.cdap.etl.api.realtime.RealtimeSource;
import co.cask.cdap.etl.api.realtime.SourceState;
import co.cask.cdap.etl.proto.v2.ETLPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Mock realtime source that emits the records it is configured to emit, then does nothing.
 */
@Plugin(type = RealtimeSource.PLUGIN_TYPE)
@Name("Mock")
public class MockSource extends RealtimeSource<StructuredRecord> {
  public static final PluginClass PLUGIN_CLASS = getPluginClass();
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(StructuredRecord.class, new StructuredRecordCodec())
    .create();
  private static final Type LIST_TYPE = new TypeToken<List<StructuredRecord>>() { }.getType();
  private final Config config;
  private List<StructuredRecord> records;

  public MockSource(Config config) {
    this.config = config;
  }

  @Override
  public void initialize(RealtimeContext context) throws Exception {
    super.initialize(context);
    records = config.getRecords();
  }

  @Nullable
  @Override
  public SourceState poll(Emitter<StructuredRecord> writer, SourceState currentState) throws Exception {
    if (currentState.getState("done") == null) {
      for (StructuredRecord record : records) {
        writer.emit(record);
      }
      currentState.setState("done", new byte[] { 0 });
    }
    return currentState;
  }

  /**
   * Config for the source.
   */
  public static class Config extends PluginConfig {
    @Nullable
    private String records;

    public Config() {
      records = "[]";
    }

    public List<StructuredRecord> getRecords() {
      return GSON.fromJson(records, LIST_TYPE);
    }
  }

  public static ETLPlugin getPlugin(List<StructuredRecord> records) {
    Map<String, String> properties = new HashMap<>();
    if (records != null) {
      properties.put("records", GSON.toJson(records));
    }
    return new ETLPlugin("Mock", RealtimeSource.PLUGIN_TYPE, properties, null);
  }

  private static PluginClass getPluginClass() {
    Map<String, PluginPropertyField> properties = new HashMap<>();
    properties.put("records", new PluginPropertyField("records", "", "string", false, false));
    return new PluginClass(RealtimeSource.PLUGIN_TYPE, "Mock", "", MockSource.class.getName(),
                           "config", properties);
  }
}
