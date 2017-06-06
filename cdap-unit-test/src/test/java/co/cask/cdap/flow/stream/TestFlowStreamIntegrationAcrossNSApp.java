/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

package co.cask.cdap.flow.stream;

import co.cask.cdap.api.annotation.Batch;
import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.flow.AbstractFlow;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

/**
 * Same as @link{TestFlowStreamIntegrationApp}, but connect to the stream in streamSpace instead of DEFAULT.
 */
public class TestFlowStreamIntegrationAcrossNSApp extends AbstractApplication {
  private static final Logger LOG = LoggerFactory.getLogger(TestFlowStreamIntegrationAcrossNSApp.class);

  @Override
  public void configure() {
    setName("TestFlowStreamIntegrationAcrossNSApp");
    setDescription("Application for testing batch stream from another namespace");
    addStream(new Stream("s1"));
    addFlow(new StreamTestFlow());
  }

  /**
   * Stream test flow across namespace.
   */
  public static class StreamTestFlow extends AbstractFlow {

    @Override
    protected void configure() {
      setName("StreamAcrossNSTestFlow");
      setDescription("Flow for testing batch stream dequeue across namespace");
      addFlowlet(new StreamReader());
      connectStream("streamSpace", "s1", "StreamReader");
      connectStream("s1", "StreamReader");
    }
  }

  /**
   * StreamReader flowlet.
   */
  public static class StreamReader extends AbstractFlowlet {

    @ProcessInput
    @Batch(100)
    public void foo(Iterator<StreamEvent> it) {
      List<StreamEvent> events = ImmutableList.copyOf(it);
      LOG.warn("Number of batched stream events = " + events.size());
      Preconditions.checkState(events.size() > 1);

      List<Integer> out = Lists.newArrayList();
      for (StreamEvent event : events) {
        out.add(Integer.parseInt(Charsets.UTF_8.decode(event.getBody()).toString()));
      }
      LOG.info("Read events=" + out);
    }
  }
}
