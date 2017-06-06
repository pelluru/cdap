/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.gateway.apps;

import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.data.stream.StreamBatchWriter;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.flow.AbstractFlow;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import co.cask.cdap.api.stream.StreamEventData;
import co.cask.cdap.api.worker.AbstractWorker;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Application writing to Stream.
 */
public class AppWritingtoStream extends AbstractApplication {

  public static final String APPNAME = "appName";
  public static final String STREAM = "myStream";
  public static final String FLOW = "flow";
  public static final String WORKER = "worker";
  public static final String DATASET = "kvTable";
  public static final String HEADER_DATASET = "headers";
  public static final String SERVICE = "srv";
  public static final String KEY = "key";
  public static final String ENDPOINT = "count";
  public static final int VALUE = 12;

  @Override
  public void configure() {
    setName(APPNAME);
    addStream(new Stream(STREAM));
    addWorker(new WritingWorker());
    addFlow(new SimpleFlow());
    addService(SERVICE, new MyServiceHandler());
    createDataset(DATASET, KeyValueTable.class);
    createDataset(HEADER_DATASET, KeyValueTable.class);
  }

  public static final class MyServiceHandler extends AbstractHttpServiceHandler {

    @UseDataSet(DATASET)
    private KeyValueTable table;

    @UseDataSet(HEADER_DATASET)
    private KeyValueTable headers;

    @GET
    @Path(ENDPOINT)
    public void process(HttpServiceRequest request, HttpServiceResponder responder) {
      responder.sendString(String.valueOf(Bytes.toLong(table.read(Bytes.toBytes(KEY)))));
    }

    @GET
    @Path("/headers/{header}")
    public void getHeader(HttpServiceRequest request, HttpServiceResponder responder,
                          @PathParam("header") String header) {
      responder.sendString(Bytes.toString(headers.read(header)));
    }
  }

  private static final class WritingWorker extends AbstractWorker {
    private static final Logger LOG = LoggerFactory.getLogger(WritingWorker.class);

    @Override
    public void configure() {
      setName(WORKER);
    }

    @Override
    public void run() {
      try {
        getContext().write(STREAM, ByteBuffer.wrap(Bytes.toBytes("Event 0")));
        getContext().write(STREAM, new StreamEventData(ImmutableMap.of("Event", "1"),
                                                       ByteBuffer.wrap(Bytes.toBytes("Event 1"))));

        File tempDir = Files.createTempDir();
        File file = File.createTempFile("abc", "tmp", tempDir);
        BufferedWriter fileWriter = Files.newWriter(file, Charsets.UTF_8);
        fileWriter.write("Event 2\n");
        fileWriter.write("Event 3");
        fileWriter.close();
        getContext().writeFile(STREAM, file, "text/plain");

        StreamBatchWriter streamBatchWriter = getContext().createBatchWriter(STREAM, "text/plain");
        streamBatchWriter.write(ByteBuffer.wrap(Bytes.toBytes("Event 4\n")));
        streamBatchWriter.write(ByteBuffer.wrap(Bytes.toBytes("Event 5\n")));
        streamBatchWriter.write(ByteBuffer.wrap(Bytes.toBytes("Event 6\n")));
        streamBatchWriter.close();

        streamBatchWriter = getContext().createBatchWriter(STREAM, "text/plain");
        streamBatchWriter.write(ByteBuffer.wrap(Bytes.toBytes("Event 7\n")));
        streamBatchWriter.write(ByteBuffer.wrap(Bytes.toBytes("Event 8\n")));
        streamBatchWriter.close();
      } catch (IOException e) {
        LOG.error(e.getMessage(), e);
      }

      for (int i = 9; i < VALUE; i++) {
        try {
          getContext().write(STREAM, String.format("Event %d", i));
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        }
      }

      try {
        getContext().write("invalidStream", "Hello");
      } catch (IOException e) {
        // no-op
      }
    }
  }

  private static final class SimpleFlow extends AbstractFlow {

    @Override
    protected void configure() {
      setName(FLOW);
      addFlowlet("flowlet", new StreamFlowlet());
      connectStream(STREAM, "flowlet");
    }
  }

  private static final class StreamFlowlet extends AbstractFlowlet {

    private static final Logger LOG = LoggerFactory.getLogger(StreamFlowlet.class);

    @UseDataSet(DATASET)
    private KeyValueTable table;

    @UseDataSet(HEADER_DATASET)
    private KeyValueTable headers;

    @ProcessInput
    public void receive(StreamEvent data) {
      table.increment(Bytes.toBytes(KEY), 1L);
      for (Map.Entry<String, String> header : data.getHeaders().entrySet()) {
        headers.write(header.getKey(), header.getValue());
      }
    }
  }
}
