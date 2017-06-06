/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package co.cask.cdap.cli.command;

import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.data.format.Formats;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.cli.ArgumentName;
import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.english.Article;
import co.cask.cdap.cli.english.Fragment;
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.cli.util.ArgumentParser;
import co.cask.cdap.client.StreamClient;
import co.cask.cdap.proto.StreamProperties;
import co.cask.cdap.proto.id.StreamId;
import co.cask.common.cli.Arguments;
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;

/**
 * Sets the Format Specification of a stream.
 */
public class SetStreamFormatCommand extends AbstractAuthCommand {

  private static final Gson GSON = new Gson();
  private final StreamClient streamClient;

  @Inject
  public SetStreamFormatCommand(StreamClient streamClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.streamClient = streamClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    StreamId streamId = cliConfig.getCurrentNamespace().stream(arguments.get(ArgumentName.STREAM.toString()));
    StreamProperties currentProperties = streamClient.getConfig(streamId);

    String formatName = arguments.get(ArgumentName.FORMAT.toString());
    Schema schema = getSchema(arguments);
    Map<String, String> settings = Collections.emptyMap();
    if (arguments.hasArgument(ArgumentName.SETTINGS.toString())) {
      settings = ArgumentParser.parseMap(arguments.get(ArgumentName.SETTINGS.toString()),
                                         ArgumentName.SETTINGS.toString());
    }
    FormatSpecification formatSpecification = new FormatSpecification(formatName, schema, settings);
    StreamProperties streamProperties = new StreamProperties(currentProperties.getTTL(),
                                                             formatSpecification,
                                                             currentProperties.getNotificationThresholdMB(),
                                                             currentProperties.getDescription());
    streamClient.setStreamProperties(streamId, streamProperties);
    output.printf("Successfully set format of stream '%s'\n", streamId.getEntityName());
  }

  private Schema getSchema(Arguments arguments) throws IOException {
    Schema schema = null;
    if (arguments.hasArgument(ArgumentName.SCHEMA.toString())) {
      // if it's a json object, try to parse it as json
      String schemaStr = arguments.get(ArgumentName.SCHEMA.toString());
      schema = isJsonObject(schemaStr) ? Schema.parseJson(schemaStr) : Schema.parseSQL(schemaStr);
    }
    return schema;
  }

  private boolean isJsonObject(String str) {
    try {
      GSON.fromJson(str, JsonObject.class);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String getPattern() {
    return String.format("set stream format <%s> <%s> [<%s>] [<%s>]", ArgumentName.STREAM, ArgumentName.FORMAT,
                         ArgumentName.SCHEMA, ArgumentName.SETTINGS);
  }

  @Override
  public String getDescription() {
    return String.format("Sets the format of %s. A valid '<%s>' is one of '%s'. A '<%s>' is an SQL-like schema " +
      "'column_name data_type, ...' or an Avro-like JSON schema. A '<%s>' is specified in the format " +
      "'key1=v1 key2=v2'.",
      Fragment.of(Article.A, ElementType.STREAM.getName()), ArgumentName.FORMAT, Joiner.on("', '").join(Formats.ALL),
      ArgumentName.SCHEMA, ArgumentName.SETTINGS);
  }
}
