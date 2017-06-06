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
package co.cask.cdap.data.stream;

import co.cask.cdap.common.io.Decoder;
import co.cask.cdap.common.io.Encoder;
import co.cask.cdap.common.io.LocationStatus;
import co.cask.cdap.common.io.Locations;
import co.cask.cdap.common.io.Processor;
import co.cask.cdap.data2.transaction.queue.QueueConstants;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.apache.twill.filesystem.Location;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collection of helper methods.
 *
 * TODO: Usage of this class needs to be refactor, as some methods are temporary (e.g. encodeMap/decodeMap).
 */
public final class StreamUtils {

  // The directory name for storing stream files that are pending for deletion
  // StreamId cannot have "." there, so it's safe that it won't clash with any stream name
  public static final String DELETED = ".deleted";

  /**
   * Decode a map.
   */
  static Map<String, String> decodeMap(Decoder decoder) throws IOException {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    int len = decoder.readInt();
    while (len != 0) {
      for (int i = 0; i < len; i++) {
        String key = decoder.readString();
        String value = decoder.readInt() == 0 ? decoder.readString() : (String) decoder.readNull();
        map.put(key, value);
      }
      len = decoder.readInt();
    }
    return map.build();
  }

  /**
   * Encodes a map.
   */
  static void encodeMap(Map<String, String> map, Encoder encoder) throws IOException {
    encoder.writeInt(map.size());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String value = entry.getValue();
      encoder.writeString(entry.getKey())
        .writeInt(value == null ? 1 : 0)
        .writeString(entry.getValue());
    }
    if (!map.isEmpty()) {
      encoder.writeInt(0);
    }
  }

  /**
   * Finds the partition name from the given event file location.
   *
   * @param eventLocation Location to the event file.
   * @return The partition name.
   * @see AbstractStreamInputFormat
   */
  public static String getPartitionName(Location eventLocation) {
    URI uri = eventLocation.toURI();
    String path = uri.getPath();
    int endIdx = path.lastIndexOf('/');
    Preconditions.checkArgument(endIdx >= 0,
                                "Invalid event path %s. Partition is missing.", uri);

    int startIdx = path.lastIndexOf('/', endIdx - 1);
    Preconditions.checkArgument(startIdx < endIdx,
                                "Invalid event path %s. Partition is missing.", uri);

    return path.substring(startIdx + 1, endIdx);
  }

  /**
   * Returns the name of the event bucket based on the file name.
   *
   * @param name Name of the file.
   * @see AbstractStreamInputFormat
   */
  public static String getBucketName(String name) {
    // Strip off the file extension
    int idx = name.lastIndexOf('.');
    return (idx >= 0) ? name.substring(0, idx) : name;
  }

  /**
   * Returns the file prefix based on the given file name.
   *
   * @param name Name of the file.
   * @return The prefix part of the stream file.
   * @see AbstractStreamInputFormat
   */
  public static String getNamePrefix(String name) {
    String bucketName = getBucketName(name);
    int idx = bucketName.lastIndexOf('.');
    Preconditions.checkArgument(idx >= 0, "Invalid name %s. Name is expected in [prefix].[seqId] format", bucketName);
    return bucketName.substring(0, idx);
  }

  /**
   * Returns the sequence number of the given file name.
   *
   * @param name Name of the file.
   * @return The sequence number of the stream file.
   * @see AbstractStreamInputFormat
   */
  public static int getSequenceId(String name) {
    String bucketName = getBucketName(name);
    int idx = bucketName.lastIndexOf('.');
    Preconditions.checkArgument(idx >= 0 && (idx + 1) < bucketName.length(),
                                "Invalid name %s. Name is expected in [prefix].[seqId] format", bucketName);
    return Integer.parseInt(bucketName.substring(idx + 1));
  }

  /**
   * Gets the partition start time based on the name of the partition.
   *
   * @return The partition start timestamp in milliseconds.
   *
   * @see AbstractStreamInputFormat
   */
  public static long getPartitionStartTime(String partitionName) {
    int idx = partitionName.indexOf('.');
    Preconditions.checkArgument(idx > 0,
                                "Invalid partition name %s. Partition name should be of format %s",
                                partitionName, "[startTimestamp].[duration]");
    return TimeUnit.MILLISECONDS.convert(Long.parseLong(partitionName.substring(0, idx)), TimeUnit.SECONDS);
  }

  /**
   * Returns true if it is valid partition name, false other. The partition name must be
   * {@code [0-9]+.[0-9]+}
   */
  public static boolean isPartition(String partitionName) {
    int dotPos = -1;
    for (int i = 0; i < partitionName.length(); i++) {
      char c = partitionName.charAt(i);
      if (c == '.') {
        // Make sure there is only one '.'
        if (dotPos >= 0) {
          return false;
        }
        dotPos = i;
        continue;
      }
      if (c < '0' || c > '9') {
        return false;
      }
    }
    // Must sure '.' is not the first character and not the last
    return dotPos > 0 && dotPos < partitionName.length() - 1;
  }

  /**
   * Gets the partition end time based on the name of the partition.
   *
   * @return the partition end timestamp in milliseconds.
   *
   * @see AbstractStreamInputFormat
   */
  public static long getPartitionEndTime(String partitionName) {
    int idx = partitionName.indexOf('.');
    Preconditions.checkArgument(idx >= 0,
                                "Invalid partition name %s. Partition name should be of format %s",
                                partitionName, "[startTimestamp].[duration]");
    long startTime = Long.parseLong(partitionName.substring(0, idx));
    long duration = Long.parseLong(partitionName.substring(idx + 1));
    return TimeUnit.MILLISECONDS.convert(startTime + duration, TimeUnit.SECONDS);
  }

  /**
   * Creates stream base location with the given generation.
   *
   * @param streamBaseLocation the base directory for the stream
   * @param generation generation id
   * @return Location for the given generation
   *
   * @see AbstractStreamInputFormat
   */
  public static Location createGenerationLocation(Location streamBaseLocation, int generation) throws IOException {
    // 0 padding generation is just for sorted view in ls. Not carry any special meaning.
    return (generation == 0) ? streamBaseLocation : streamBaseLocation.append(String.format("%06d", generation));
  }

  /**
   * Creates the location for the partition directory.
   *
   * @param baseLocation Base location for partition directory.
   * @param partitionStart Partition start timestamp in milliseconds.
   * @param partitionDuration Partition duration in milliseconds.
   * @return The location for the partition directory.
   */
  public static Location createPartitionLocation(Location baseLocation,
                                                 long partitionStart, long partitionDuration) throws IOException {
    // 0 padding is just for sorted view in ls. Not carry any special meaning.
    String path = String.format("%010d.%05d",
                                TimeUnit.SECONDS.convert(partitionStart, TimeUnit.MILLISECONDS),
                                TimeUnit.SECONDS.convert(partitionDuration, TimeUnit.MILLISECONDS));

    return baseLocation.append(path);
  }

  /**
   * Creates location for stream file.
   *
   * @param partitionLocation The partition directory location.
   * @param prefix File prefix.
   * @param seqId Sequence number of the file.
   * @param type Type of the stream file.
   * @return The location of the stream file.
   *
   * @see AbstractStreamInputFormat for naming convention.
   */
  public static Location createStreamLocation(Location partitionLocation, String prefix,
                                              int seqId, StreamFileType type) throws IOException {
    // 0 padding sequence id is just for sorted view in ls. Not carry any special meaning.
    return partitionLocation.append(String.format("%s.%06d.%s", prefix, seqId, type.getSuffix()));
  }

  /**
   * Returns the aligned partition start time.
   *
   * @param timestamp Timestamp in milliseconds.
   * @param partitionDuration Partition duration in milliseconds.
   * @return The partition start time of the given timestamp.
   */
  public static long getPartitionStartTime(long timestamp, long partitionDuration) {
    return timestamp / partitionDuration * partitionDuration;
  }

  /**
   * Encode a {@link StreamFileOffset} instance.
   *
   * @param out Output for encoding
   * @param offset The offset object to encode
   */
  public static void encodeOffset(DataOutput out, StreamFileOffset offset) throws IOException {
    out.writeInt(offset.getGeneration());
    out.writeLong(offset.getPartitionStart());
    out.writeLong(offset.getPartitionEnd());
    out.writeUTF(offset.getNamePrefix());
    out.writeInt(offset.getSequenceId());
    out.writeLong(offset.getOffset());
  }

  /**
   * Decode a {@link StreamFileOffset} encoded by the {@link #encodeOffset(DataOutput, StreamFileOffset)}
   * method.
   *
   * @param config Stream configuration for the stream that the offset is representing
   * @param in Input for decoding
   * @return A new instance of {@link StreamFileOffset}
   */
  public static StreamFileOffset decodeOffset(StreamConfig config, DataInput in) throws IOException {
    int generation = in.readInt();
    long partitionStart = in.readLong();
    long duration = in.readLong() - partitionStart;
    String prefix = in.readUTF();
    int seqId = in.readInt();
    long offset = in.readLong();

    Location baseLocation = config.getLocation();
    if (generation > 0) {
      baseLocation = createGenerationLocation(baseLocation, generation);
    }
    Location partitionLocation = createPartitionLocation(baseLocation, partitionStart, duration);
    Location eventLocation = createStreamLocation(partitionLocation, prefix, seqId, StreamFileType.EVENT);
    return new StreamFileOffset(eventLocation, offset, generation);
  }

  public static StreamConfig ensureExists(StreamAdmin admin, StreamId streamId) throws IOException {
    try {
      return admin.getConfig(streamId);
    } catch (Exception e) {
      // Ignored
    }
    try {
      admin.create(streamId);
      return admin.getConfig(streamId);
    } catch (Exception e) {
      Throwables.propagateIfInstanceOf(e, IOException.class);
      throw new IOException(e);
    }
  }

  /**
   * Finds the current generation id of a stream. It scans the stream directory to look for largest generation
   * number in directory name.
   *
   * @param config configuration of the stream
   * @return the generation id
   */
  public static int getGeneration(StreamConfig config) throws IOException {
    return getGeneration(config.getLocation());
  }

  /**
   * Finds the current generation if of a stream. It scans the stream directory to look for largest generation
   * number in directory name.
   *
   * @param streamLocation location to scan for generation id
   * @return the generation id
   */
  public static int getGeneration(Location streamLocation) throws IOException {
    // Default generation is 0.
    int genId = 0;
    CharMatcher numMatcher = CharMatcher.inRange('0', '9');

    List<Location> locations = streamLocation.list();
    if (locations == null) {
      return 0;
    }

    for (Location location : locations) {
      if (numMatcher.matchesAllOf(location.getName()) && location.isDirectory()) {
        int id = Integer.parseInt(location.getName());
        if (id > genId) {
          genId = id;
        }
      }
    }
    return genId;
  }

  /**
   * Finds the next sequence id for the given partition with the given file prefix.
   *
   * @param partitionLocation the directory where the stream partition is
   * @param filePrefix prefix of file name to match
   * @return the next sequence id, which is the current max id + 1.
   * @throws IOException if failed to find the next sequence id
   */
  public static int getNextSequenceId(Location partitionLocation, String filePrefix) throws IOException {
    // Try to find the file of this bucket with the highest sequence number.
    int maxSequence = -1;
    for (Location location : partitionLocation.list()) {
      String fileName = location.getName();
      if (!fileName.startsWith(filePrefix)) {
        continue;
      }
      StreamUtils.getSequenceId(fileName);

      int idx = fileName.lastIndexOf('.');
      if (idx < filePrefix.length()) {
        // Ignore file with invalid stream file name
        continue;
      }

      try {
        // File name format is [prefix].[sequenceId].[dat|idx]
        int seq = StreamUtils.getSequenceId(fileName);
        if (seq > maxSequence) {
          maxSequence = seq;
        }
      } catch (NumberFormatException e) {
        // Ignore stream file with invalid sequence id
      }
    }
    return maxSequence + 1;
  }

  /**
   * Get the size of the data persisted for the stream under the given stream location.
   *
   * @param streamLocation stream to get data size of
   * @return the size of the data persisted for the stream which config is the {@code streamName}
   * @throws IOException in case of any error in fetching the size
   */
  public static long fetchStreamFilesSize(Location streamLocation) throws IOException {
    Processor<LocationStatus, Long> processor = new Processor<LocationStatus, Long>() {
      private long size = 0;
      @Override
      public boolean process(LocationStatus input) {
        if (!input.isDir() && StreamFileType.EVENT.isMatched(input.getUri().getPath())) {
          size += input.getLength();
        }
        return true;
      }

      @Override
      public Long getResult() {
        return size;
      }
    };

    List<Location> locations = streamLocation.list();
    // All directories are partition directories
    for (Location location : locations) {
      if (!location.isDirectory() || !isPartition(location.getName())) {
        continue;
      }
      Locations.processLocations(location, false, processor);
    }
    return processor.getResult();
  }

  /**
   * Gets a TableId for stream consumer state stores within a given namespace.
   * @param namespace the namespace for which the table is for.
   * @return constructed TableId. Note that the namespace in the returned TableId is the CDAP namespace (CDAP-7344).
   */
  public static TableId getStateStoreTableId(NamespaceId namespace) {
    String tableName = String.format("%s.%s.state.store",
                                     NamespaceId.SYSTEM.getNamespace(), QueueConstants.QueueType.STREAM.toString());
    return TableId.from(namespace.getNamespace(), tableName);
  }

  /**
   * Gets the stream name given a stream's base directory.
   * @param streamBaseLocation the location of the stream's directory
   * @return name of the stream associated with the location
   */
  public static String getStreamNameFromLocation(Location streamBaseLocation) {
    // streamBaseLocation = /.../<namespace>/streams/<streamName>,
    // or /customLocation/streams/<streamname>
    // as constructed by FileStreamAdmin#getStreamConfigLocation
    Location streamsDir = Locations.getParent(streamBaseLocation);
    Preconditions.checkNotNull(streamsDir,
                               "Streams directory of stream base location %s was null.", streamBaseLocation);
    return streamBaseLocation.getName();
  }

  /**
   * Returns the location of the stream deleted location.
   *
   * @param streamRootLocation the root location that all streams go under
   */
  public static Location getDeletedLocation(Location streamRootLocation) throws IOException {
    return streamRootLocation.append(DELETED);
  }

  /**
   * Lists all stream locations under the given root.
   *
   * @param streamRootLocation the root location that all streams go under
   */
  public static Iterable<Location> listAllStreams(Location streamRootLocation) throws IOException {
    return Iterables.filter(streamRootLocation.list(), new Predicate<Location>() {
      @Override
      public boolean apply(Location location) {
        // Any directories started with "." is special system file, which is not regular stream directory
        return !location.getName().startsWith(".");
      }
    });
  }

  private StreamUtils() {
  }
}
