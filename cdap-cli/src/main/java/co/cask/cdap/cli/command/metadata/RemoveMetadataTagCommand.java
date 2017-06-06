/*
 * Copyright © 2015-2017 Cask Data, Inc.
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
 * the License
 */

package co.cask.cdap.cli.command.metadata;

import co.cask.cdap.cli.ArgumentName;
import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.util.AbstractCommand;
import co.cask.cdap.client.MetadataClient;
import co.cask.cdap.proto.id.EntityId;
import co.cask.common.cli.Arguments;
import com.google.inject.Inject;

import java.io.PrintStream;

/**
 * Removes a metadata tag for an entity.
 */
public class RemoveMetadataTagCommand extends AbstractCommand {

  private final MetadataClient client;

  @Inject
  public RemoveMetadataTagCommand(CLIConfig cliConfig, MetadataClient client) {
    super(cliConfig);
    this.client = client;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    EntityId entity = EntityId.fromString(arguments.get(ArgumentName.ENTITY.toString()));
    String tag = arguments.get("tag");
    client.removeTag(entity.toId(), tag);
    output.println("Successfully removed metadata tag");
  }

  @Override
  public String getPattern() {
    return String.format("remove metadata-tag <%s> <tag>", ArgumentName.ENTITY);
  }

  @Override
  public String getDescription() {
    return "Removes a specific metadata tag for an entity. " + ArgumentName.ENTITY_DESCRIPTION_STRING;
  }
}
