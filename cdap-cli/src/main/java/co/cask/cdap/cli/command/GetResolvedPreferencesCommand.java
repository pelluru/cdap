/*
 * Copyright © 2017 Cask Data, Inc.
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

import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.english.Article;
import co.cask.cdap.cli.english.Fragment;
import co.cask.cdap.client.PreferencesClient;

/**
 * Get Resolved Preferences for instance, namespace, application, program
 */
public class GetResolvedPreferencesCommand extends AbstractGetPreferencesCommand {
  private final ElementType type;

  protected GetResolvedPreferencesCommand(ElementType type, PreferencesClient client, CLIConfig cliConfig) {
    super(type, client, cliConfig, true);
    this.type = type;
  }

  @Override
  public String getPattern() {
    return determinePattern();
  }

  @Override
  public String getDescription() {
    return String.format("Gets the resolved preferences of %s", Fragment.of(Article.A, type.getName()));
  }
}
