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
package co.cask.cdap.data.stream;

import co.cask.cdap.api.data.format.FormatSpecification;
import co.cask.cdap.api.data.format.RecordFormat;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.api.stream.StreamEventDecoder;
import co.cask.cdap.data.stream.decoder.BytesStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.FormatStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.IdentityStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.StringStreamEventDecoder;
import co.cask.cdap.data.stream.decoder.TextStreamEventDecoder;
import co.cask.cdap.format.RecordFormats;
import co.cask.cdap.internal.io.SchemaTypeAdapter;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.security.Principal;
import co.cask.cdap.security.spi.authentication.AuthenticationContext;
import co.cask.cdap.security.spi.authorization.AuthorizationEnforcer;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Stream mapreduce input format. Stream data files are organized by partition directories with bucket files inside.
 *
 * <br/><br/>
 *   Each file has path pattern
 * <pre>
 *     [streamName]/[partitionName]/[bucketName].[dat|idx]
 * OR
 *     [streamName]/[generation]/[partitionName]/[bucketName].[dat|idx]
 * </pre>
 * Where {@code .dat} is the event data file, {@code .idx} is the accompany index file.
 *
 * <br/><br/>
 * The {@code generation} is an integer, representing the stream generation of the data under it. When a stream
 * is truncated, the generation increment by one. The generation {@code 0} is a special case that there is
 * no generation directory.
 *
 * <br/><br/>
 * The {@code partitionName} is formatted as
 * <pre>
 *   [partitionStartTime].[duration]
 * </pre>
 * with both {@code partitionStartTime} and {@code duration} in seconds.
 *
 * <br/><br/>
 * The {@code bucketName} is formatted as
 * <pre>
 *   [prefix].[seqNo]
 * </pre>
 * The {@code seqNo} is a strictly increasing integer for the same prefix starting with 0.
 *
 * @param <K> Key type of input
 * @param <V> Value type of input
 */
public abstract class AbstractStreamInputFormat<K, V> extends InputFormat<K, V> {
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(Schema.class, new SchemaTypeAdapter())
    .create();
  private static final StreamInputSplitFactory<InputSplit> splitFactory = new StreamInputSplitFactory<InputSplit>() {
    @Override
    public InputSplit createSplit(Path eventPath, Path indexPath, long startTime, long endTime,
                                  long start, long length, @Nullable String[] locations) {
      return new StreamInputSplit(eventPath, indexPath, startTime, endTime, start, length, locations);
    }
  };

  // Keys for storing in job configuration
  private static final String EVENT_START_TIME = "input.streaminputformat.event.starttime";
  private static final String EVENT_END_TIME = "input.streaminputformat.event.endtime";
  private static final String STREAM_PATH = "input.streaminputformat.stream.path";
  private static final String STREAM_TTL = "input.streaminputformat.stream.event.ttl";
  private static final String MAX_SPLIT_SIZE = "input.streaminputformat.max.splits.size";
  private static final String MIN_SPLIT_SIZE = "input.streaminputformat.min.splits.size";
  private static final String DECODER_TYPE = "input.streaminputformat.decoder.type";
  private static final String BODY_FORMAT = "input.streaminputformat.stream.body.format";
  private static final String STREAM_ID = "input.streaminputformat.stream.id";

  /**
   * Sets the TTL for the stream events.
   *
   * @param conf  The configuration to modify
   * @param ttl TTL of the stream in milliseconds.
   */
  public static void setTTL(Configuration conf, long ttl) {
    Preconditions.checkArgument(ttl >= 0, "TTL must be >= 0");
    conf.setLong(STREAM_TTL, ttl);
  }

  /**
   * Sets the time range for the stream events.
   *
   * @param conf The configuration to modify
   * @param startTime Timestamp in milliseconds of the event start time (inclusive).
   * @param endTime Timestamp in milliseconds of the event end time (exclusive).
   */
  public static void setTimeRange(Configuration conf, long startTime, long endTime) {
    Preconditions.checkArgument(startTime >= 0, "Start time must be >= 0");
    Preconditions.checkArgument(endTime >= 0, "End time must be >= 0");

    conf.setLong(EVENT_START_TIME, startTime);
    conf.setLong(EVENT_END_TIME, endTime);
  }

  /**
   * Sets the stream id of the stream.
   *
   * @param conf The conf to modify.
   * @param streamId {@link StreamId} id of the stream.
   */
  public static void setStreamId(Configuration conf, StreamId streamId) {
    conf.set(STREAM_ID, GSON.toJson(streamId));
  }

  /**
   * Sets the base path to stream files.
   *
   * @param conf The conf to modify.
   * @param path The file path to stream base directory.
   */
  public static void setStreamPath(Configuration conf, URI path) {
    conf.set(STREAM_PATH, path.toString());
  }

  /**
   * Sets the maximum split size.
   *
   * @param conf The conf to modify.
   * @param maxSplits Maximum split size in bytes.
   */
  public static void setMaxSplitSize(Configuration conf, long maxSplits) {
    conf.setLong(MAX_SPLIT_SIZE, maxSplits);
  }

  /**
   * Sets the minimum split size.
   *
   * @param conf The conf to modify.
   * @param minSplits Minimum split size in bytes.
   */
  public static void setMinSplitSize(Configuration conf, long minSplits) {
    conf.setLong(MIN_SPLIT_SIZE, minSplits);
  }

  /**
   * Sets the class name for the {@link StreamEventDecoder}.
   *
   * @param conf The conf to modify.
   * @param decoderClassName Class name of the decoder class
   */
  public static void setDecoderClassName(Configuration conf, String decoderClassName) {
    conf.set(DECODER_TYPE, decoderClassName);
  }

  /**
   * Returns the {@link StreamEventDecoder} class as specified in the job configuration.
   *
   * @param conf The job configuration
   * @return The {@link StreamEventDecoder} class or {@code null} if it is not set.
   */
  @SuppressWarnings("unchecked")
  public static <K, V> Class<? extends StreamEventDecoder<K, V>> getDecoderClass(Configuration conf) {
    return (Class<? extends StreamEventDecoder<K, V>>) conf.getClass(DECODER_TYPE, null, StreamEventDecoder.class);
  }

  /**
   * Set the format specification for reading the body of stream events. Will also set the decoder class appropriately.
   *
   * @param conf The job configuration.
   * @param formatSpecification Format specification for reading the body of stream events.
   */
  public static void setBodyFormatSpecification(Configuration conf, FormatSpecification formatSpecification) {
    conf.set(BODY_FORMAT, GSON.toJson(formatSpecification));
    setDecoderClassName(conf, FormatStreamEventDecoder.class.getName());
  }

  /**
   * Tries to set the {@link AbstractStreamInputFormat#DECODER_TYPE} depending upon the supplied value class
   *
   * @param conf   the conf to modify
   * @param vClass the value class Type
   */
  public static void inferDecoderClass(Configuration conf, Type vClass) {
    if (Text.class.equals(vClass)) {
      setDecoderClassName(conf, TextStreamEventDecoder.class.getName());
      return;
    }
    if (String.class.equals(vClass)) {
      setDecoderClassName(conf, StringStreamEventDecoder.class.getName());
      return;
    }
    if (BytesWritable.class.equals(vClass)) {
      setDecoderClassName(conf, BytesStreamEventDecoder.class.getName());
      return;
    }
    if (vClass instanceof Class && ((Class<?>) vClass).isAssignableFrom(StreamEvent.class)) {
      setDecoderClassName(conf, IdentityStreamEventDecoder.class.getName());
      return;
    }

    throw new IllegalArgumentException("The value class must be of type BytesWritable, Text, StreamEvent or " +
                                         "StreamEventData if no decoder type is provided");
  }

  /**
   * Return {@link AuthorizationEnforcer} to enforce authorization permissions.
   */
  public abstract AuthorizationEnforcer getAuthorizationEnforcer(TaskAttemptContext context);

  /**
   * Return {@link AuthenticationContext} to determine the {@link Principal}.
   */
  public abstract AuthenticationContext getAuthenticationContext(TaskAttemptContext context);

  @Override
  public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException {
    Configuration conf = context.getConfiguration();
    long ttl = conf.getLong(STREAM_TTL, Long.MAX_VALUE);
    long endTime = conf.getLong(EVENT_END_TIME, Long.MAX_VALUE);
    long startTime = Math.max(conf.getLong(EVENT_START_TIME, 0L), getCurrentTime() - ttl);
    long maxSplitSize = conf.getLong(MAX_SPLIT_SIZE, Long.MAX_VALUE);
    long minSplitSize = Math.min(conf.getLong(MIN_SPLIT_SIZE, 1L), maxSplitSize);
    StreamInputSplitFinder<InputSplit> splitFinder = StreamInputSplitFinder
      .builder(URI.create(conf.get(STREAM_PATH)))
      .setStartTime(startTime)
      .setEndTime(endTime)
      .setMinSplitSize(minSplitSize)
      .setMaxSplitSize(maxSplitSize)
      .build(splitFactory);
    return splitFinder.getSplits(conf);
  }

  @Override
  public RecordReader<K, V> createRecordReader(InputSplit split,
                                               TaskAttemptContext context) throws IOException, InterruptedException {
    return new StreamRecordReader<>(createStreamEventDecoder(context.getConfiguration()),
                                    getAuthorizationEnforcer(context),
                                    getAuthenticationContext(context),
                                    GSON.fromJson(context.getConfiguration().get(STREAM_ID), StreamId.class));
  }

  protected long getCurrentTime() {
    return System.currentTimeMillis();
  }

  @SuppressWarnings("unchecked")
  protected StreamEventDecoder<K, V> createStreamEventDecoder(Configuration conf) {
    Class<? extends StreamEventDecoder> decoderClass = getDecoderClass(conf);
    Preconditions.checkNotNull(decoderClass, "Failed to load stream event decoder %s", conf.get(DECODER_TYPE));
    try {
      // if this is a FormatStreamEventDecoder, we need to create and initialize the format that will be used
      // to format the stream body.
      if (decoderClass.isAssignableFrom(FormatStreamEventDecoder.class)) {
        try {
          RecordFormat<StreamEvent, V> bodyFormat = getInitializedFormat(conf);
          return (StreamEventDecoder<K, V>) new FormatStreamEventDecoder(bodyFormat);
        } catch (Exception e) {
          throw new IllegalArgumentException("Unable to get the stream body format.");
        }
      } else {
        return (StreamEventDecoder<K, V>) decoderClass.newInstance();
      }
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private RecordFormat<StreamEvent, V> getInitializedFormat(Configuration conf)
    throws UnsupportedTypeException, IllegalAccessException, ClassNotFoundException, InstantiationException {
    String formatSpecStr = conf.get(BODY_FORMAT);
    if (formatSpecStr == null || formatSpecStr.isEmpty()) {
      throw new IllegalArgumentException(
        BODY_FORMAT + " must be set in the configuration in order to use a format for the stream body.");
    }
    FormatSpecification formatSpec = GSON.fromJson(formatSpecStr, FormatSpecification.class);
    return RecordFormats.createInitializedFormat(formatSpec);
  }
}
