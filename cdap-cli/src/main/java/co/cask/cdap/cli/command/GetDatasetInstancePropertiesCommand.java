/*
 * Copyright © 2016 Cask Data, Inc.
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

import co.cask.cdap.cli.ArgumentName;
import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.english.Article;
import co.cask.cdap.cli.english.Fragment;
import co.cask.cdap.cli.util.AbstractCommand;
import co.cask.cdap.cli.util.table.Table;
import co.cask.cdap.client.DatasetClient;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.common.cli.Arguments;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Sets properties for a dataset instance.
 */
public class GetDatasetInstancePropertiesCommand extends AbstractCommand {

  private final DatasetClient datasetClient;

  @Inject
  public GetDatasetInstancePropertiesCommand(DatasetClient datasetClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.datasetClient = datasetClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    DatasetId instance = cliConfig.getCurrentNamespace().dataset(
      arguments.get(ArgumentName.DATASET.toString()));

    Map<String, String> properties = datasetClient.getProperties(instance);
    Table table = Table.builder()
      .setHeader("property", "value")
      .setRows(Iterables.transform(properties.entrySet(), new Function<Map.Entry<String, String>, List<String>>() {
        @Nullable
        @Override
        public List<String> apply(Map.Entry<String, String> entry) {
          return ImmutableList.of(entry.getKey(), entry.getValue());
        }
      }))
      .build();
    cliConfig.getTableRenderer().render(cliConfig, output, table);
  }

  @Override
  public String getPattern() {
    return String.format("get dataset instance properties <%s>",
                         ArgumentName.DATASET);
  }

  @Override
  public String getDescription() {
    return String.format("Gets the properties used to create or update %s",
                         Fragment.of(Article.A, ElementType.DATASET.getName()));
  }
}
