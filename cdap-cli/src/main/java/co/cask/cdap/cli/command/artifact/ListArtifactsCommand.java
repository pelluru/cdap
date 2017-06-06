/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.cli.command.artifact;

import co.cask.cdap.api.artifact.ArtifactScope;
import co.cask.cdap.api.artifact.ArtifactSummary;
import co.cask.cdap.cli.ArgumentName;
import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.cli.util.RowMaker;
import co.cask.cdap.cli.util.table.Table;
import co.cask.cdap.client.ArtifactClient;
import co.cask.common.cli.Arguments;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.util.List;

/**
 * Lists all artifacts.
 */
public class ListArtifactsCommand extends AbstractAuthCommand {

  private final ArtifactClient artifactClient;

  @Inject
  public ListArtifactsCommand(ArtifactClient artifactClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.artifactClient = artifactClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {

    List<ArtifactSummary> artifactSummaries;
    String artifactScope = arguments.getOptional(ArgumentName.SCOPE.toString());
    if (artifactScope == null) {
      artifactSummaries = artifactClient.list(cliConfig.getCurrentNamespace());
    } else {
      artifactSummaries = artifactClient.list(cliConfig.getCurrentNamespace(),
                                              ArtifactScope.valueOf(artifactScope.toUpperCase()));
    }
    Table table = Table.builder()
      .setHeader("name", "version", "scope")
      .setRows(artifactSummaries, new RowMaker<ArtifactSummary>() {
        @Override
        public List<?> makeRow(ArtifactSummary object) {
          return Lists.newArrayList(object.getName(), object.getVersion(), object.getScope().name());
        }
      }).build();
    cliConfig.getTableRenderer().render(cliConfig, output, table);
  }

  @Override
  public String getPattern() {
    return String.format("list artifacts [<%s>]", ArgumentName.SCOPE);
  }

  @Override
  public String getDescription() {
    return String.format("Lists all %s. If no scope is provided, artifacts in all scopes are returned. " +
      "Otherwise, only artifacts in the specified scope are returned.", ElementType.ARTIFACT.getNamePlural());
  }
}
