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

import co.cask.cdap.cli.ArgumentName;
import co.cask.cdap.cli.CLIConfig;
import co.cask.cdap.cli.Categorized;
import co.cask.cdap.cli.CommandCategory;
import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.english.Article;
import co.cask.cdap.cli.english.Fragment;
import co.cask.cdap.cli.util.AbstractAuthCommand;
import co.cask.cdap.client.StreamClient;
import co.cask.cdap.proto.id.StreamId;
import co.cask.common.cli.Arguments;

import java.io.PrintStream;
import javax.inject.Inject;

/**
 * Sends an event to a stream.
 */
public class SendStreamEventCommand extends AbstractAuthCommand implements Categorized {

  private final StreamClient streamClient;

  @Inject
  public SendStreamEventCommand(StreamClient streamClient, CLIConfig cliConfig) {
    super(cliConfig);
    this.streamClient = streamClient;
  }

  @Override
  public void perform(Arguments arguments, PrintStream output) throws Exception {
    StreamId streamId = cliConfig.getCurrentNamespace().stream(arguments.get(ArgumentName.STREAM.toString()));
    String streamEvent = arguments.get(ArgumentName.STREAM_EVENT.toString());
    streamClient.sendEvent(streamId, streamEvent);
    output.printf("Successfully sent stream event to stream '%s'\n", streamId.getEntityName());
  }

  @Override
  public String getPattern() {
    return String.format("send stream <%s> <%s>", ArgumentName.STREAM, ArgumentName.STREAM_EVENT);
  }

  @Override
  public String getDescription() {
    return String.format("Sends an event to %s", Fragment.of(Article.A, ElementType.STREAM.getName()));
  }

  @Override
  public String getCategory() {
    return CommandCategory.INGEST.getName();
  }
}
