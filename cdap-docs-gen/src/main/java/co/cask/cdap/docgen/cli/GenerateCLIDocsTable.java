/*
 * Copyright © 2012-2014 Cask Data, Inc.
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

package co.cask.cdap.docgen.cli;

import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.LaunchOptions;
import co.cask.cdap.cli.commandset.DefaultCommands;
import co.cask.cdap.cli.util.table.CsvTableRenderer;
import co.cask.cdap.cli.util.table.TableRenderer;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.common.cli.Command;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

/**
 * Generates data for the table in cdap-docs/reference-manual/source/cli-api.rst.
 */
public class GenerateCLIDocsTable {

  private final Command printDocsCommand;

  public GenerateCLIDocsTable(final CLIConfig cliConfig) throws URISyntaxException, IOException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CConfiguration.class).toInstance(CConfiguration.create());
        bind(LaunchOptions.class).toInstance(LaunchOptions.DEFAULT);
        bind(PrintStream.class).toInstance(System.out);
        bind(CLIConfig.class).toInstance(cliConfig);
        bind(ClientConfig.class).toInstance(cliConfig.getClientConfig());
        bind(TableRenderer.class).toInstance(new CsvTableRenderer());
      }
    });
    this.printDocsCommand = new GenerateCLIDocsTableCommand(injector.getInstance(DefaultCommands.class),
                                                            injector.getInstance(CLIConfig.class));
  }

  public static void main(String[] args) throws Exception {
    PrintStream output = System.out;

    CLIConfig config = new CLIConfig();
    GenerateCLIDocsTable generateCLIDocsTable = new GenerateCLIDocsTable(config);
    generateCLIDocsTable.printDocsCommand.execute(null, output);
  }
}
