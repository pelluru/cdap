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

package co.cask.cdap.test.app;

import co.cask.cdap.api.annotation.Output;
import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.batch.Input;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.flow.AbstractFlow;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.Callback;
import co.cask.cdap.api.flow.flowlet.FailurePolicy;
import co.cask.cdap.api.flow.flowlet.FailureReason;
import co.cask.cdap.api.flow.flowlet.InputContext;
import co.cask.cdap.api.flow.flowlet.OutputEmitter;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.api.mapreduce.AbstractMapReduce;
import co.cask.cdap.api.mapreduce.MapReduceContext;
import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.api.service.BasicService;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Map;
import java.util.StringTokenizer;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 *
 */
public class WordCountApp extends AbstractApplication {
  @Override
  public void configure() {
    setName("WordCountApp");
    addStream(new Stream("text"));
    addDatasetModule("my-kv", MyKeyValueTableDefinition.Module.class);
    createDataset("mydataset", "myKeyValueTable", DatasetProperties.EMPTY);
    createDataset("totals", "myKeyValueTable", DatasetProperties.EMPTY);
    addFlow(new WordCountFlow());
    addService(new BasicService("WordFrequency", new WordFrequencyHandler()));
    addMapReduce(new CountTotal());
    addMapReduce(new CountFromStream());
  }

  /**
   * Output object of stream source.
   */
  public static final class MyRecord {

    private final String title;
    private final String text;
    private final boolean expired;

    public MyRecord(String title, String text, boolean expired) {
      this.title = title;
      this.text = text;
      this.expired = expired;
    }

    public String getTitle() {
      return title;
    }

    public String getText() {
      return text;
    }

    public boolean isExpired() {
      return expired;
    }

    @Override
    public String toString() {
      return "CompatibleRecord{" +
        "title='" + title + '\'' +
        ", text='" + text + '\'' +
        ", expired=" + expired +
        '}';
    }
  }

  /**
   * Flow that counts words coming from stream source.
   */
  public static class WordCountFlow extends AbstractFlow {

    @Override
    protected void configure() {
      setName("WordCountFlow");
      setDescription("Flow for counting words");
      addFlowlet(new StreamSource());
      addFlowlet(new Tokenizer());
      addFlowlet(new CountByField());
      connectStream("text", "StreamSource");
      connect("StreamSource", "Tokenizer");
      connect("Tokenizer", "CountByField");
    }
  }

  /**
   * Stream source for word count flow.
   */
  public static final class StreamSource extends AbstractFlowlet {
    private OutputEmitter<MyRecord> output;
    private Metrics metrics;

    @ProcessInput
    public void process(StreamEvent event, InputContext context) throws CharacterCodingException {
      if (!"text".equals(context.getOrigin())) {
        return;
      }

      metrics.count("stream.event", 1);

      ByteBuffer buf = event.getBody();
      output.emit(new MyRecord(
        event.getHeaders().get("title"),
        buf == null ? null : Charsets.UTF_8.newDecoder().decode(buf).toString(),
        false));
    }
  }

  /**
   * Tokenizer for word count flow.
   */
  public static class Tokenizer extends AbstractFlowlet {
    @Output("field")
    private OutputEmitter<Map<String, String>> outputMap;

    private boolean error = true;

    @ProcessInput
    public void foo(MyRecord data) {
      tokenize(data.getTitle(), "title");
      tokenize(data.getText(), "text");
      if (error) {
        error = false;
        throw new IllegalStateException(data.toString());
      }
    }

    private void tokenize(String str, String field) {
      if (str == null) {
        return;
      }
      final String delimiters = "[ .-]";
      for (String token : str.split(delimiters)) {
        outputMap.emit(ImmutableMap.of("field", field, "word", token));
      }
    }
  }

  /**
   * Flow that counts words and stores them in a table.
   */
  public static class CountByField extends AbstractFlowlet implements Callback {
    @UseDataSet("mydataset")
    private MyKeyValueTableDefinition.KeyValueTable counters;

    @ProcessInput("field")
    public void process(Map<String, String> fieldToken) {

      String token = fieldToken.get("word");
      if (token == null) {
        return;
      }
      String field = fieldToken.get("field");
      if (field != null) {
        token = field + ":" + token;
      }

      Long current = Long.valueOf(counters.get(token, "0"));
      counters.put(token, String.valueOf(current + 1));
    }

    @Override
    public void onSuccess(@Nullable Object input, @Nullable InputContext inputContext) {
    }

    @Override
    public FailurePolicy onFailure(@Nullable Object input, @Nullable InputContext inputContext, FailureReason reason) {
      return FailurePolicy.RETRY;
    }
  }

  /**
   * Service handler to query word counts.
   */
  public static class WordFrequencyHandler extends AbstractHttpServiceHandler {
    @UseDataSet("mydataset")
    private MyKeyValueTableDefinition.KeyValueTable counters;

    @UseDataSet("totals")
    private MyKeyValueTableDefinition.KeyValueTable totals;

    @GET
    @Path("wordfreq/{word}")
    public void wordfreq(HttpServiceRequest request, HttpServiceResponder responder,
                          @PathParam("word") String word)
      throws IOException {
      Map<String, Long> result = ImmutableMap.of(word, Long.valueOf(this.counters.get(word, "0")));
      responder.sendJson(result);
    }

    @GET
    @Path("total")
    public void total(HttpServiceRequest request, HttpServiceResponder responder) throws IOException {
      long result = Long.valueOf(this.totals.get("total_words_count"));
      responder.sendJson(result);
    }

    @GET
    @Path("stream_total")
    public void streamTotal(HttpServiceRequest request, HttpServiceResponder responder) throws IOException {
      long result = Long.valueOf(this.totals.get("stream_total_words_count"));
      responder.sendJson(result);
    }
  }

  /**
   * Map Reduce to count total of counts.
   */
  public static class CountTotal extends AbstractMapReduce {
    @Override
    public void configure() {
      setName("countTotal");
    }

    @Override
    public void initialize() throws Exception {
      MapReduceContext context = getContext();
      Job job = context.getHadoopJob();
      job.setMapperClass(MyMapper.class);
      job.setReducerClass(MyReducer.class);
      context.addInput(Input.ofDataset("mydataset"));
      context.addOutput(co.cask.cdap.api.data.batch.Output.ofDataset("totals"));
    }

    /**
     * Mapper for map reduce job.
     */
    public static class MyMapper extends Mapper<String, String, BytesWritable, LongWritable> {
      @Override
      protected void map(String key, String value, Context context) throws IOException, InterruptedException {
        context.write(new BytesWritable(Bytes.toBytes("total")), new LongWritable(Long.valueOf(value)));
      }
    }

    /**
     * Reducer for map reduce job.
     */
    public static class MyReducer extends Reducer<BytesWritable, LongWritable, String, String> {
      @Override
      protected void reduce(BytesWritable key, Iterable<LongWritable> values, Context context)
        throws IOException, InterruptedException {

        long total = 0;
        for (LongWritable longWritable : values) {
          total += longWritable.get();
        }

        context.write("total_words_count", String.valueOf(total));
      }
    }
  }

  /**
   * Performs word count from stream data directly.
   */
  public static final class CountFromStream extends AbstractMapReduce {

    @Override
    public void configure() {
      setName("countFromStream");
    }

    @Override
    public void initialize() throws Exception {
      MapReduceContext context = getContext();
      Job job = context.getHadoopJob();
      job.setMapperClass(StreamMapper.class);
      job.setMapOutputKeyClass(Text.class);
      job.setMapOutputValueClass(LongWritable.class);
      job.setReducerClass(StreamReducer.class);
      context.addInput(Input.ofStream("text"));
      context.addOutput(co.cask.cdap.api.data.batch.Output.ofDataset("totals"));
    }

    /**
     * Mapper for the count from stream.
     */
    public static final class StreamMapper extends Mapper<LongWritable, Text, Text, LongWritable> {

      private static final Text TOTAL = new Text("total");

      @Override
      protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        StringTokenizer itr = new StringTokenizer(value.toString());
        long total = 0;
        while (itr.hasMoreTokens()) {
          total++;
          itr.nextToken();
        }
        context.write(TOTAL, new LongWritable(total));
      }
    }

    /**
     * Reducer for the count from stream.
     */
    public static final class StreamReducer extends Reducer<Text, LongWritable, String, String> {

      @Override
      protected void reduce(Text key, Iterable<LongWritable> values,
                            Context context) throws IOException, InterruptedException {
        long sum = 0;
        for (LongWritable val : values) {
          sum += val.get();
        }
        context.write("stream_total_words_count", String.valueOf(sum));
      }
    }
  }

}
