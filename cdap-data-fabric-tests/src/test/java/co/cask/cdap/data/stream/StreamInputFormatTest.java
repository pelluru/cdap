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
package co.cask.cdap.data.stream;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.api.stream.GenericStreamEventData;
import co.cask.cdap.api.stream.StreamEventData;
import co.cask.cdap.api.stream.StreamEventDecoder;
import co.cask.cdap.data.stream.decoder.BytesStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.IdentityStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.StringStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.TextStreamEventDecoder;
import co.cask.cdap.format.TextRecordFormat;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.security.auth.context.AuthenticationTestContext;
import co.cask.cdap.security.spi.authentication.AuthenticationContext;
import co.cask.cdap.security.spi.authorization.AuthorizationEnforcer;
import co.cask.cdap.security.spi.authorization.NoOpAuthorizer;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobContextImpl;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 *
 */
public class StreamInputFormatTest {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final long CURRENT_TIME = 2000;
  private static final StreamId DUMMY_ID = new StreamId("ns", "str");

  @Test
  public void testTTL() throws Exception {
    File inputDir = tmpFolder.newFolder();
    File outputDir = tmpFolder.newFolder();

    outputDir.delete();

    final long currentTime = CURRENT_TIME;
    final long ttl = 1500;

    // Write 500 events in one bucket under one partition, with timestamps 0..499 by 1
    // This partition file should be skipped by AbstractStreamFileConsumerFactory
    generateEvents(inputDir, 500, 0, 1, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        return "expiredEvent " + timestamp;
      }
    });

    // Write 1000 events in one bucket under a different partition, with timestamps 0..999 by 1
    generateEvents(inputDir, 1000, 0, 1, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        if (timestamp + ttl < currentTime) {
          return "expiredEvent " + timestamp;
        } else {
          return "nonExpiredEvent " + timestamp;
        }
      }
    });

    // Write 1000 events in one bucket under a different partition, with timestamps 1000..1999 by 1
    generateEvents(inputDir, 1000, 1000, 1, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        return "nonExpiredEvent " + timestamp;
      }
    });

    // Run MR with TTL = 1500, currentTime = CURRENT_TIME
    runMR(inputDir, outputDir, 0, Long.MAX_VALUE, 2000, ttl);

    // Verify the result. It should have 1500 "nonExpiredEvent {timestamp}" for timestamp 500..1999 by 1.
    Map<String, Integer> output = loadMRResult(outputDir);
    Assert.assertEquals(ttl + 1, output.size());
    Assert.assertEquals(null, output.get("expiredEvent"));
    Assert.assertEquals(ttl, output.get("nonExpiredEvent").intValue());
    for (long i = (currentTime - ttl); i < currentTime; i++) {
      Assert.assertEquals(1, output.get(Long.toString(i)).intValue());
    }
  }

  @Test
  public void testTTLMultipleEventsWithSameTimestamp() throws Exception {
    File inputDir = tmpFolder.newFolder();
    File outputDir = tmpFolder.newFolder();

    outputDir.delete();

    final long currentTime = CURRENT_TIME;
    final long ttl = 1;

    // Write 1000 events in one bucket under one partition, with timestamp currentTime - ttl - 1
    generateEvents(inputDir, 1000, currentTime - ttl - 1, 0, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        return "expiredEvent " + timestamp;
      }
    });

    // Write 1000 events in one bucket under a different partition, with currentTime
    generateEvents(inputDir, 1000, currentTime, 0, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        return "nonExpiredEvent " + timestamp;
      }
    });

    runMR(inputDir, outputDir, 0, Long.MAX_VALUE, 2000, ttl);

    // Verify the result. It should have 1000 "nonExpiredEvent {currentTime}".
    Map<String, Integer> output = loadMRResult(outputDir);
    Assert.assertEquals(2, output.size());
    Assert.assertEquals(null, output.get("expiredEvent"));
    Assert.assertEquals(1000, output.get("nonExpiredEvent").intValue());
    Assert.assertEquals(1000, output.get(Long.toString(currentTime)).intValue());
  }

  @Test
  public void testAllEvents() throws Exception {
    // Write 1000 events in one bucket under one partition.
    File inputDir = tmpFolder.newFolder();
    File outputDir = tmpFolder.newFolder();

    outputDir.delete();

    generateEvents(inputDir);
    runMR(inputDir, outputDir, 0, Long.MAX_VALUE, 1000, Long.MAX_VALUE);

    // Verify the result. It should have 1000 "testing", and 100 for each integers in 0..9.
    Map<String, Integer> output = loadMRResult(outputDir);
    Assert.assertEquals(11, output.size());
    Assert.assertEquals(1000, output.get("Testing").intValue());
    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(100, output.get(Integer.toString(i)).intValue());
    }
  }

  @Test
  public void testTimeRange() throws Exception {
    // Write 1000 events in one bucket under one partition.
    File inputDir = tmpFolder.newFolder();
    File outputDir = tmpFolder.newFolder();

    outputDir.delete();

    generateEvents(inputDir);
    // Run a MapReduce on 1 timestamp only.
    runMR(inputDir, outputDir, 1401, 1402, 1000, Long.MAX_VALUE);

    // Verify the result. It should have 1 "testing", and 1 "1".
    Map<String, Integer> output = loadMRResult(outputDir);
    Assert.assertEquals(2, output.size());
    Assert.assertEquals(1, output.get("Testing").intValue());
    Assert.assertEquals(1, output.get("1").intValue());
  }

  @Test
  public void testLiveStream() throws Exception {
    File inputDir = tmpFolder.newFolder();
    File outputDir = tmpFolder.newFolder();

    outputDir.delete();

    // Write 2 events, and keep the writer open
    File partition = new File(inputDir, "0.1000");
    File eventFile = new File(partition, "bucket.1.0." + StreamFileType.EVENT.getSuffix());
    File indexFile = new File(partition, "bucket.1.0." + StreamFileType.INDEX.getSuffix());

    partition.mkdirs();

    StreamDataFileWriter writer = new StreamDataFileWriter(Files.newOutputStreamSupplier(eventFile),
                                                           Files.newOutputStreamSupplier(indexFile),
                                                           100L);

    writer.append(StreamFileTestUtils.createEvent(0, "Testing 0"));
    writer.append(StreamFileTestUtils.createEvent(1, "Testing 1"));

    writer.flush();

    // Run MapReduce to process all data.
    runMR(inputDir, outputDir, 0, Long.MAX_VALUE, 1000, Long.MAX_VALUE);
    Map<String, Integer> output = loadMRResult(outputDir);

    Assert.assertEquals(3, output.size());
    Assert.assertEquals(2, output.get("Testing").intValue());
    Assert.assertEquals(1, output.get("0").intValue());
    Assert.assertEquals(1, output.get("1").intValue());
  }

  @Test
  public void testIdentityStreamEventDecoder() {
    ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();
    headers.put("key1", "value1");
    headers.put("key2", "value2");
    ByteBuffer buffer = Charsets.UTF_8.encode("testdata");
    StreamEvent event = new StreamEvent(headers.build(), buffer, System.currentTimeMillis());
    StreamEventDecoder<LongWritable, StreamEvent> decoder = new IdentityStreamEventDecoder();
    StreamEventDecoder.DecodeResult<LongWritable, StreamEvent> result
      = new StreamEventDecoder.DecodeResult<>();
    result = decoder.decode(event, result);
    Assert.assertEquals(new LongWritable(event.getTimestamp()), result.getKey());
    Assert.assertEquals(event, result.getValue());
  }

  @Test
  public void testStringStreamEventDecoder() {
    String body = "Testing";
    StreamEvent event = new StreamEvent(ImmutableMap.<String, String>of(), Charsets.UTF_8.encode(body));
    StreamEventDecoder<LongWritable, String> decoder = new StringStreamEventDecoder();
    StreamEventDecoder.DecodeResult<LongWritable, String> result
      = new StreamEventDecoder.DecodeResult<>();
    result = decoder.decode(event, result);

    Assert.assertEquals(event.getTimestamp(), result.getKey().get());
    Assert.assertEquals(body, result.getValue());
  }

  @Test
  public void testStreamDecoderInference() {
    Configuration conf = new Configuration();
    AbstractStreamInputFormat.inferDecoderClass(conf, BytesWritable.class);
    Assert.assertEquals(BytesStreamEventDecoder.class, AbstractStreamInputFormat.getDecoderClass(conf));
    AbstractStreamInputFormat.inferDecoderClass(conf, Text.class);
    Assert.assertEquals(TextStreamEventDecoder.class, AbstractStreamInputFormat.getDecoderClass(conf));
    AbstractStreamInputFormat.inferDecoderClass(conf, String.class);
    Assert.assertEquals(StringStreamEventDecoder.class, AbstractStreamInputFormat.getDecoderClass(conf));
    AbstractStreamInputFormat.inferDecoderClass(conf, StreamEvent.class);
    Assert.assertEquals(IdentityStreamEventDecoder.class, AbstractStreamInputFormat.getDecoderClass(conf));
    AbstractStreamInputFormat.inferDecoderClass(conf, StreamEventData.class);
    Assert.assertEquals(IdentityStreamEventDecoder.class, AbstractStreamInputFormat.getDecoderClass(conf));
  }

  @Test
  public void testStreamRecordReader() throws Exception {
    File inputDir = tmpFolder.newFolder();
    File partition = new File(inputDir,  "1.1000");
    partition.mkdirs();
    File eventFile = new File(partition, "bucket.1.0." + StreamFileType.EVENT.getSuffix());
    File indexFile = new File(partition, "bucket.1.0." + StreamFileType.INDEX.getSuffix());

    // write 1 event
    StreamDataFileWriter writer = new StreamDataFileWriter(Files.newOutputStreamSupplier(eventFile),
                                                           Files.newOutputStreamSupplier(indexFile),
                                                           100L);
    writer.append(StreamFileTestUtils.createEvent(1000, "test"));
    writer.flush();

    // get splits from the input format. Expect to get 2 splits,
    // one from 0 - some offset and one from offset - Long.MAX_VALUE.
    Configuration conf = new Configuration();
    TaskAttemptContext context = new TaskAttemptContextImpl(conf, new TaskAttemptID());
    AbstractStreamInputFormat.setStreamId(conf, DUMMY_ID);
    AbstractStreamInputFormat.setStreamPath(conf, inputDir.toURI());
    AbstractStreamInputFormat format = new AbstractStreamInputFormat() {

      @Override
      public AuthorizationEnforcer getAuthorizationEnforcer(TaskAttemptContext context) {
        return new NoOpAuthorizer();
      }

      @Override
      public AuthenticationContext getAuthenticationContext(TaskAttemptContext context) {
        return new AuthenticationTestContext();
      }
    };
    List<InputSplit> splits = format.getSplits(new JobContextImpl(new JobConf(conf), new JobID()));
    Assert.assertEquals(2, splits.size());

    // write another event so that the 2nd split has something to read
    writer.append(StreamFileTestUtils.createEvent(1001, "test"));
    writer.close();

    // create a record reader for the 2nd split
    StreamRecordReader<LongWritable, StreamEvent> recordReader =
      new StreamRecordReader<>(new IdentityStreamEventDecoder(), new NoOpAuthorizer(), new AuthenticationTestContext(),
                               DUMMY_ID);
    recordReader.initialize(splits.get(1), context);

    // check that we read the 2nd stream event
    Assert.assertTrue(recordReader.nextKeyValue());
    StreamEvent output = recordReader.getCurrentValue();
    Assert.assertEquals(1001, output.getTimestamp());
    Assert.assertEquals("test", Bytes.toString(output.getBody()));
    // check that there is nothing more to read
    Assert.assertFalse(recordReader.nextKeyValue());
  }

  @Test
  public void testFormatStreamRecordReader() throws IOException, InterruptedException {
    File inputDir = tmpFolder.newFolder();
    File partition = new File(inputDir,  "1.1000");
    partition.mkdirs();
    File eventFile = new File(partition, "bucket.1.0." + StreamFileType.EVENT.getSuffix());
    File indexFile = new File(partition, "bucket.1.0." + StreamFileType.INDEX.getSuffix());

    // write 1 event
    StreamDataFileWriter writer = new StreamDataFileWriter(Files.newOutputStreamSupplier(eventFile),
                                                           Files.newOutputStreamSupplier(indexFile),
                                                           100L);

    StreamEvent streamEvent = new StreamEvent(ImmutableMap.of("header1", "value1", "header2", "value2"),
                                              Charsets.UTF_8.encode("hello world"),
                                              1000);
    writer.append(streamEvent);
    writer.close();

    FormatSpecification formatSpec =
      new FormatSpecification(TextRecordFormat.class.getName(),
                              Schema.recordOf("event", Schema.Field.of("body", Schema.of(Schema.Type.STRING))),
                              Collections.<String, String>emptyMap());
    Configuration conf = new Configuration();
    AbstractStreamInputFormat.setStreamId(conf, DUMMY_ID);
    AbstractStreamInputFormat.setBodyFormatSpecification(conf, formatSpec);
    AbstractStreamInputFormat.setStreamPath(conf, inputDir.toURI());
    TaskAttemptContext context = new TaskAttemptContextImpl(conf, new TaskAttemptID());

    AbstractStreamInputFormat format = new AbstractStreamInputFormat() {

      @Override
      public AuthorizationEnforcer getAuthorizationEnforcer(TaskAttemptContext context) {
        return new NoOpAuthorizer();
      }

      @Override
      public AuthenticationContext getAuthenticationContext(TaskAttemptContext context) {
        return new AuthenticationTestContext();
      }
    };

    // read all splits and store the results in the list
    List<GenericStreamEventData<StructuredRecord>> recordsRead = Lists.newArrayList();
    List<InputSplit> inputSplits = format.getSplits(context);
    for (InputSplit split : inputSplits) {
      RecordReader<LongWritable, GenericStreamEventData<StructuredRecord>> recordReader =
        format.createRecordReader(split, context);
      recordReader.initialize(split, context);
      while (recordReader.nextKeyValue()) {
        recordsRead.add(recordReader.getCurrentValue());
      }
    }

    // should only have read 1 record
    Assert.assertEquals(1, recordsRead.size());
    GenericStreamEventData<StructuredRecord> eventData = recordsRead.get(0);
    Assert.assertEquals(streamEvent.getHeaders(), eventData.getHeaders());
    Assert.assertEquals("hello world", eventData.getBody().get("body"));
  }

  private void generateEvents(File inputDir, int numEvents, long startTime, long timeIncrement,
                              GenerateEvent generator) throws IOException {
    File partition = new File(inputDir, Long.toString(startTime / 1000) + ".1000");
    File eventFile = new File(partition, "bucket.1.0." + StreamFileType.EVENT.getSuffix());
    File indexFile = new File(partition, "bucket.1.0." + StreamFileType.INDEX.getSuffix());

    partition.mkdirs();

    StreamDataFileWriter writer = new StreamDataFileWriter(Files.newOutputStreamSupplier(eventFile),
                                                           Files.newOutputStreamSupplier(indexFile),
                                                           100L);
    // Write 1000 events
    for (int i = 0; i < numEvents; i++) {
      long timestamp = startTime + i * timeIncrement;
      writer.append(StreamFileTestUtils.createEvent(timestamp, generator.generate(i, timestamp)));
    }

    writer.close();
  }

  private void generateEvents(File inputDir) throws IOException {
    generateEvents(inputDir, 1000, 1000, 1, new GenerateEvent() {
      @Override
      public String generate(int index, long timestamp) {
        return "Testing " + (index % 10);
      }
    });
  }

  private void runMR(File inputDir, File outputDir, long startTime, long endTime,
                     long splitSize, long ttl) throws Exception {

    Job job = Job.getInstance();
    Configuration conf = job.getConfiguration();

    AbstractStreamInputFormat.setStreamId(conf, DUMMY_ID);
    AbstractStreamInputFormat.setTTL(conf, ttl);
    AbstractStreamInputFormat.setStreamPath(conf, inputDir.toURI());
    AbstractStreamInputFormat.setTimeRange(conf, startTime, endTime);
    AbstractStreamInputFormat.setMaxSplitSize(conf, splitSize);
    job.setInputFormatClass(TestStreamInputFormat.class);

    TextOutputFormat.setOutputPath(job, new Path(outputDir.toURI()));
    job.setOutputFormatClass(TextOutputFormat.class);

    job.setJarByClass(StreamInputFormatTest.class);
    job.setMapperClass(TokenizeMapper.class);
    job.setReducerClass(AggregateReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(LongWritable.class);
    job.setMapOutputValueClass(IntWritable.class);

    job.waitForCompletion(true);
  }

  private Map<String, Integer> loadMRResult(File outputDir) throws IOException {
    Map<String, Integer> output = Maps.newTreeMap();
    BufferedReader reader = Files.newReader(new File(outputDir, "part-r-00000"), Charsets.UTF_8);
    try {
      String line = reader.readLine();
      while (line != null) {
        int idx = line.indexOf('\t');
        output.put(line.substring(0, idx), Integer.parseInt(line.substring(idx + 1)));
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
    return output;
  }

  private interface GenerateEvent {
    String generate(int index, long timestamp);
  }

  /**
   * StreamInputFormat for testing.
   */
  private static final class TestStreamInputFormat extends AbstractStreamInputFormat<LongWritable, Text> {

    @Override
    protected StreamEventDecoder<LongWritable, Text> createStreamEventDecoder(Configuration conf) {
      return new TextStreamEventDecoder();
    }

    @Override
    public AuthorizationEnforcer getAuthorizationEnforcer(TaskAttemptContext context) {
      return new NoOpAuthorizer();
    }

    @Override
    public AuthenticationContext getAuthenticationContext(TaskAttemptContext context) {
      return new AuthenticationTestContext();
    }

    @Override
    protected long getCurrentTime() {
      return CURRENT_TIME;
    }
  }

  /**
   * Mapper for testing.
   */
  public static final class TokenizeMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    private static final IntWritable ONE = new IntWritable(1);
    private final Text word = new Text();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
      StringTokenizer itr = new StringTokenizer(value.toString());
      while (itr.hasMoreTokens()) {
        word.set(itr.nextToken());
        context.write(word, ONE);
      }
    }
  }

  /**
   * Reducer for testing.
   */
  public static final class AggregateReducer extends Reducer<Text, IntWritable, Text, LongWritable> {

    private final LongWritable result = new LongWritable();

    @Override
    protected void reduce(Text key, Iterable<IntWritable> values,
                          Context context) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }
}
