/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

import co.cask.cdap.cli.command.ExecuteQueryCommand;
import co.cask.cdap.cli.command.PreferencesCommandSet;
import co.cask.common.cli.Command;
import co.cask.common.cli.CommandSet;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Default set of commands.
 */
public class DefaultCommands extends CommandSet<Command> {

  @Inject
  public DefaultCommands(Injector injector) {
    super(
      ImmutableList.<Command>builder()
        .add(injector.getInstance(ExecuteQueryCommand.class))
        .build(),
      ImmutableList.<CommandSet<Command>>builder()
        .add(injector.getInstance(GeneralCommands.class))
        .add(injector.getInstance(MetricsCommands.class))
        .add(injector.getInstance(ApplicationCommands.class))
        .add(injector.getInstance(ArtifactCommands.class))
        .add(injector.getInstance(StreamCommands.class))
        .add(injector.getInstance(ProgramCommands.class))
        .add(injector.getInstance(DatasetCommands.class))
        .add(injector.getInstance(ServiceCommands.class))
        .add(injector.getInstance(PreferencesCommandSet.class))
        .add(injector.getInstance(NamespaceCommands.class))
        .add(injector.getInstance(ScheduleCommands.class))
        .add(injector.getInstance(SecurityCommands.class))
        .add(injector.getInstance(LineageCommands.class))
        .add(injector.getInstance(MetadataCommands.class))
        .build());
  }
}
