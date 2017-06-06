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
import co.cask.cdap.cli.english.Article;
import co.cask.cdap.cli.english.Fragment;
import co.cask.cdap.cli.exception.CommandInputError;
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.client.ProgramClient;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.common.cli.Arguments;

import java.io.PrintStream;

/**
 * Gets the status of a program.
 */
public class GetProgramStatusCommand extends AbstractAuthCommand {

  private final ProgramClient programClient;
  private final ElementType elementType;

  protected GetProgramStatusCommand(ElementType elementType, ProgramClient programClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.elementType = elementType;
    this.programClient = programClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    String[] programIdParts = arguments.get(elementType.getArgumentName().toString()).split("\\.");
    if (programIdParts.length < 2) {
      throw new CommandInputError(this);
    }

    String appId = programIdParts[0];
    String programName = programIdParts[1];
    ProgramId programId = cliConfig.getCurrentNamespace().app(appId).program(elementType.getProgramType(), programName);
    String status = programClient.getStatus(programId);
    output.println(status);
  }

  @Override
  public String getPattern() {
    return String.format("get %s status <%s>", elementType.getShortName(), elementType.getArgumentName());
  }

  @Override
  public String getDescription() {
    return String.format("Gets the status of %s", Fragment.of(Article.A, elementType.getName()));
  }
}
