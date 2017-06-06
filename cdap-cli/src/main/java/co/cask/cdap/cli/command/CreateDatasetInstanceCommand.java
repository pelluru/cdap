/*
 * Copyright © 2014-2017 Cask Data, Inc.
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
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.cli.util.ArgumentParser;
import co.cask.cdap.client.DatasetClient;
import co.cask.cdap.proto.DatasetInstanceConfiguration;
import co.cask.common.cli.Arguments;
import com.google.gson.Gson;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.util.Map;

/**
 * Creates a dataset.
 */
public class CreateDatasetInstanceCommand extends AbstractAuthCommand {

  private static final Gson GSON = new Gson();
  private final DatasetClient datasetClient;

  @Inject
  public CreateDatasetInstanceCommand(DatasetClient datasetClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.datasetClient = datasetClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    String datasetType = arguments.get(ArgumentName.DATASET_TYPE.toString());
    String datasetName = arguments.get(ArgumentName.NEW_DATASET.toString());
    String datasetPropertiesString = arguments.getOptional(ArgumentName.DATASET_PROPERTIES.toString(), "");
    String datasetDescription = arguments.getOptional(ArgumentName.DATASET_DESCRIPTON.toString(), null);
    Map<String, String> datasetProperties = ArgumentParser.parseMap(datasetPropertiesString,
                                                                    ArgumentName.DATASET_PROPERTIES.toString());
    String datasetOwner = arguments.getOptional(ArgumentName.PRINCIPAL.toString(), null);

    // TODO: CDAP-8110 (Rohit) Support owner principal in CLI by deprecating this command and introducing a more user
    // friendly create dataset instance command
    DatasetInstanceConfiguration datasetConfig =
      new DatasetInstanceConfiguration(datasetType, datasetProperties, datasetDescription, datasetOwner);

    datasetClient.create(cliConfig.getCurrentNamespace().dataset(datasetName), datasetConfig);

    StringBuilder builder = new StringBuilder(String.format("Successfully created dataset named '%s' with type " +
                                                              "'%s', properties '%s'", datasetName, datasetType,
                                                            GSON.toJson(datasetProperties)));
    if (datasetDescription != null) {
      builder.append(String.format(", description '%s'", datasetDescription));
    }
    if (datasetOwner != null) {
      builder.append(String.format(", owner principal '%s'", datasetOwner));
    }
    output.printf(builder.toString());
    output.println();
  }

  @Override
  public String getPattern() {
    return String.format("create dataset instance <%s> <%s> [<%s>] [<%s>] [%s <%s>]",
                         ArgumentName.DATASET_TYPE, ArgumentName.NEW_DATASET, ArgumentName.DATASET_PROPERTIES,
                         ArgumentName.DATASET_DESCRIPTON, ArgumentName.PRINCIPAL, ArgumentName.PRINCIPAL);
  }

  @Override
  public String getDescription() {
    return String.format("Creates %s instance of the specified %s. Can optionally take %s, %s, or %s where '<%s>' " +
                           "is in the format 'key1=val1 key2=val2' and '<%s>' is the Kerberos principal of the owner " +
                           "of the dataset.", Fragment.of(Article.A, ElementType.DATASET.getName()),
                         ArgumentName.DATASET_TYPE, ArgumentName.DATASET_PROPERTIES, ArgumentName.DATASET_DESCRIPTON,
                         ArgumentName.PRINCIPAL, ArgumentName.DATASET_PROPERTIES, ArgumentName.PRINCIPAL);
  }
}
