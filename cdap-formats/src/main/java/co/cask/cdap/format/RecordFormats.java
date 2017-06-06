/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.format;

import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.data.format.Formats;
import co.cask.cdap.api.data.format.RecordFormat;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Creates {@link RecordFormat} objects given the name of a format. Names are first checked against standard names like
 * "CSV" or "TSV". If they are not a standard name, they are interpreted as fully qualified class names.
 */
public final class RecordFormats {
  // We may eventually want this mapping to be derived from the config.
  private static final Map<String, Class<? extends RecordFormat>> NAME_CLASS_MAP =
    ImmutableMap.<String, Class<? extends RecordFormat>>builder()
      .put(Formats.TEXT, TextRecordFormat.class)
      .put(Formats.CSV, DelimitedStringsRecordFormat.class)
      .put(Formats.TSV, DelimitedStringsRecordFormat.class)
      .put(Formats.AVRO, AvroRecordFormat.class)
      .put(Formats.COMBINED_LOG_FORMAT, CombinedLogRecordFormat.class)
      .put(Formats.GROK, GrokRecordFormat.class)
      .put(Formats.SYSLOG, GrokRecordFormat.class)
      .build();
  private static final Map<String, Map<String, String>> NAME_SETTINGS_MAP =
    ImmutableMap.<String, Map<String, String>>builder()
      .put(Formats.CSV, ImmutableMap.of(DelimitedStringsRecordFormat.DELIMITER, ","))
      .put(Formats.TSV, ImmutableMap.of(DelimitedStringsRecordFormat.DELIMITER, "\t"))
      .put(Formats.SYSLOG, GrokRecordFormat.settings("%{SYSLOGLINE:syslogline}"))
      .build();
  private static final Map<String, Schema> DEFAULT_SCHEMA_MAP =
    ImmutableMap.<String, Schema>builder()
      .put(Formats.SYSLOG, Schema.recordOf(
        "streamEvent",
        Schema.Field.of("timestamp", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
        Schema.Field.of("logsource", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
        Schema.Field.of("program", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
        Schema.Field.of("message", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
        Schema.Field.of("pid", Schema.nullableOf(Schema.of(Schema.Type.STRING)))))
      .build();

  /**
   * Create an initialized record format for the given format specification. The name in the specification is
   * first checked against standard names like "CSV" or "TSV". If it is a standard name, the corresponding
   * format will be created, with specification settings applied on top of default settings.
   * For example, "CSV" will map to the {@link DelimitedStringsRecordFormat}, with a comma as the delimiter,
   * whereas "TSV" will map to the {@link DelimitedStringsRecordFormat}, with a tab as the delimiter.
   * If the name is not a standard name, it is interpreted as a class name.
   *
   * @param spec the specification for the format to create and initialize
   * @param <FROM> Type of underlying object the format reads
   * @param <TO> Type of object the format reads the underlying object into
   * @return Initialized {@link RecordFormat} based on the given name
   * @throws IllegalAccessException if there was an illegal access when instantiating the record format
   * @throws InstantiationException if there was an exception instantiating the record format
   * @throws ClassNotFoundException if the record format class could not be found
   * @throws UnsupportedTypeException if the specification is not supported by the format
   */
  public static <FROM, TO> RecordFormat<FROM, TO> createInitializedFormat(FormatSpecification spec)
    throws IllegalAccessException, InstantiationException, ClassNotFoundException, UnsupportedTypeException {
    String name = spec.getName();

    // check if it's a standard class
    Class<? extends RecordFormat> formatClass = NAME_CLASS_MAP.get(name.toLowerCase());
    @SuppressWarnings("unchecked")
    RecordFormat<FROM, TO> format = (RecordFormat<FROM, TO>) (formatClass == null ?
      Class.forName(name).newInstance() : formatClass.newInstance());

    // compute actual settings: use default settings if present
    Map<String, String> settings;
    Map<String, String> defaultSettings = NAME_SETTINGS_MAP.get(name.toLowerCase());
    if (defaultSettings != null) {
      settings = Maps.newHashMap(defaultSettings);
      if (spec.getSettings() != null) {
        settings.putAll(spec.getSettings());
      }
    } else {
      settings = spec.getSettings();
    }

    // compute actual schema
    Schema schema;
    Schema defaultSchema = DEFAULT_SCHEMA_MAP.get(name.toLowerCase());
    if (defaultSchema != null && spec.getSchema() == null) {
      schema = defaultSchema;
    } else {
      schema = spec.getSchema();
    }

    FormatSpecification actualSpec = new FormatSpecification(name, schema, settings);
    format.initialize(actualSpec);
    return format;
  }
}
