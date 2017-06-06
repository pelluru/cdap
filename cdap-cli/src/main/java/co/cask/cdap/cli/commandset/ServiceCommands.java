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

package co.cask.cdap.cli.commandset;

import co.cask.cdap.cli.command.CallServiceCommand;
import co.cask.cdap.cli.command.CheckServiceAvailabilityCommand;
import co.cask.cdap.cli.command.DeleteRouteConfigCommand;
import co.cask.cdap.cli.command.GetRouteConfigCommand;
import co.cask.cdap.cli.command.GetServiceEndpointsCommand;
import co.cask.cdap.cli.command.SetRouteConfigCommand;
import co.cask.common.cli.Command;
import co.cask.common.cli.CommandSet;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Service commands.
 */
public class ServiceCommands extends CommandSet<Command> {

  @Inject
  public ServiceCommands(Injector injector) {
    super(
      ImmutableList.<Command>builder()
        .add(injector.getInstance(CallServiceCommand.class))
        .add(injector.getInstance(GetServiceEndpointsCommand.class))
        .add(injector.getInstance(CheckServiceAvailabilityCommand.class))
        .add(injector.getInstance(SetRouteConfigCommand.class))
        .add(injector.getInstance(GetRouteConfigCommand.class))
        .add(injector.getInstance(DeleteRouteConfigCommand.class))
        .build());
  }
}
