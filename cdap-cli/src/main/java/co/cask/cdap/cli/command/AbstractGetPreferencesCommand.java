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

package co.cask.cdap.cli.command;

import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.util.AbstractCommand;
import co.cask.cdap.client.PreferencesClient;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.UnauthenticatedException;
import co.cask.cdap.security.spi.authorization.UnauthorizedException;
import co.cask.common.cli.Arguments;
import com.google.common.base.Joiner;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

/**
 * Abstract Class for getting preferences for instance, namespace, application, program.
 */
public abstract class AbstractGetPreferencesCommand extends AbstractCommand {
  private final PreferencesClient client;
  private final ElementType type;
  private final boolean resolved;

  protected AbstractGetPreferencesCommand(ElementType type, PreferencesClient client, CLIConfig cliConfig,
                                          boolean resolved) {
    super(cliConfig);
    this.type = type;
    this.client = client;
    this.resolved = resolved;
  }

  private String joinMapEntries(Map<String, String> map) {
    return Joiner.on(String.format("%n")).join(map.entrySet().iterator());
  }

  @Override
  public void perform(Arguments arguments, PrintStream printStream) throws Exception {
    printStream.print(joinMapEntries(parsePreferences(arguments)));
  }

  @Deprecated
  protected String determineDeprecatedPattern() {
    String action = resolved ? "get resolved" : "get";
      switch (type) {
        case INSTANCE:
        case NAMESPACE:
          return String.format("%s preferences %s", action, type.getShortName());
        case APP:
        case FLOW:
        case MAPREDUCE:
        case WORKFLOW:
        case SERVICE:
        case WORKER:
        case SPARK:
          return String.format("%s preferences %s <%s>", action, type.getShortName(), type.getArgumentName());
      }
      throw new RuntimeException("Unrecognized element type: " + type.getShortName());
  }

  protected String determinePattern() {
    String action = resolved ? "get resolved" : "get";
    switch (type) {
      case INSTANCE:
      case NAMESPACE:
        return String.format("%s %s preferences", action, type.getShortName());
      case APP:
      case FLOW:
      case MAPREDUCE:
      case WORKFLOW:
      case SERVICE:
      case WORKER:
      case SPARK:
        return String.format("%s %s preferences <%s>", action, type.getShortName(), type.getArgumentName());
    }
    throw new RuntimeException("Unrecognized element type: " + type.getShortName());
  }

  private Map<String, String> parsePreferences(Arguments arguments)
    throws IOException, UnauthenticatedException, NotFoundException, UnauthorizedException {

    String[] programIdParts = new String[0];
    if (arguments.hasArgument(type.getArgumentName().toString())) {
      programIdParts = arguments.get(type.getArgumentName().toString()).split("\\.");
    }
    switch(type) {
      case INSTANCE:
        checkInputLength(programIdParts, 0);
        return client.getInstancePreferences();
      case NAMESPACE:
        checkInputLength(programIdParts, 0);
        return client.getNamespacePreferences(cliConfig.getCurrentNamespace(), resolved);
      case APP:
        return client.getApplicationPreferences(parseApplicationId(arguments), resolved);
      case FLOW:
      case MAPREDUCE:
      case WORKFLOW:
      case SERVICE:
      case SPARK:
        return client.getProgramPreferences(parseProgramId(arguments, type), resolved);
      default:
        throw new IllegalArgumentException("Unrecognized element type for preferences: "  + type.getShortName());
    }
  }
}
