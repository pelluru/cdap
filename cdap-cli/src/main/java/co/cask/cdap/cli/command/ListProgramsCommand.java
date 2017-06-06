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

package co.cask.cdap.cli.command;

import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.cli.util.RowMaker;
import co.cask.cdap.cli.util.table.Table;
import co.cask.cdap.client.ApplicationClient;
import co.cask.cdap.proto.ProgramRecord;
import co.cask.cdap.proto.ProgramType;
import co.cask.common.cli.Arguments;
import com.google.common.collect.Lists;

import java.io.PrintStream;
import java.util.List;

/**
 * Lists all programs of a certain type.
 */
public class ListProgramsCommand extends AbstractAuthCommand {

  private final ApplicationClient appClient;
  private final ProgramType programType;

  public ListProgramsCommand(ProgramType programType, ApplicationClient appClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.programType = programType;
    this.appClient = appClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    List<ProgramRecord> programs = appClient.listAllPrograms(cliConfig.getCurrentNamespace(), programType);

    Table table = Table.builder()
      .setHeader("app", "id", "description")
      .setRows(programs, new RowMaker<ProgramRecord>() {
        @Override
        public List<?> makeRow(ProgramRecord object) {
          return Lists.newArrayList(object.getApp(), object.getName(), object.getDescription());
        }
      }).build();
    cliConfig.getTableRenderer().render(cliConfig, output, table);
  }

  @Override
  public String getPattern() {
    return String.format("list %s", programType.getCategoryName());
  }

  @Override
  public String getDescription() {
    return String.format("Lists all %s", getElementType().getNamePlural());
  }

  private ElementType getElementType() {
    return ElementType.fromProgramType(programType);
  }
}
