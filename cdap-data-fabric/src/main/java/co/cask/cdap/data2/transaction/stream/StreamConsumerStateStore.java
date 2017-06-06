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
package co.cask.cdap.data2.transaction.stream;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.data.stream.StreamFileOffset;
import co.cask.cdap.data.stream.StreamUtils;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Represents storage for {@link ConsumerState} for stream consumers.
 */
public abstract class StreamConsumerStateStore implements ConsumerStateStore<StreamConsumerState,
                                                                             Iterable<StreamFileOffset>> {

  protected final StreamConfig streamConfig;
  protected final StreamId streamId;

  protected StreamConsumerStateStore(StreamConfig streamConfig) {
    this.streamConfig = streamConfig;
    this.streamId = streamConfig.getStreamId();
  }

  @Override
  public final void getAll(Collection<? super StreamConsumerState> result) throws IOException {
    SortedMap<byte[], byte[]> states = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    fetchAll(streamId.toBytes(), states);

    for (Map.Entry<byte[], byte[]> entry : states.entrySet()) {
      byte[] column = entry.getKey();
      byte[] value = entry.getValue();
      if (value != null) {
        result.add(new StreamConsumerState(getGroupId(column), getInstanceId(column), decodeOffsets(value)));
      }
    }
  }

  @Override
  public final void getByGroup(long groupId, Collection<? super StreamConsumerState> result) throws IOException {
    SortedMap<byte[], byte[]> states = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    fetchAll(streamId.toBytes(), Bytes.toBytes(groupId), states);

    for (Map.Entry<byte[], byte[]> entry : states.entrySet()) {
      byte[] column = entry.getKey();
      if (getGroupId(column) != groupId) {
        continue;
      }
      byte[] value = entry.getValue();
      if (value != null) {
        result.add(new StreamConsumerState(groupId, getInstanceId(column), decodeOffsets(value)));
      }
    }
  }

  @Override
  public final StreamConsumerState get(long groupId, int instanceId) throws IOException {
    byte[] value = fetch(streamId.toBytes(), getColumn(groupId, instanceId));
    return value == null ? null : new StreamConsumerState(groupId, instanceId, decodeOffsets(value));
  }

  @Override
  public final void save(StreamConsumerState state) throws IOException {
    store(streamId.toBytes(), getColumn(state.getGroupId(), state.getInstanceId()), encodeOffsets(state.getState()));
  }

  @Override
  public final void save(Iterable<? extends StreamConsumerState> states) throws IOException {
    ImmutableSortedMap.Builder<byte[], byte[]> values = ImmutableSortedMap.orderedBy(Bytes.BYTES_COMPARATOR);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    DataOutput output = new DataOutputStream(os);
    for (StreamConsumerState state : states) {
      os.reset();
      encodeOffsets(state.getState(), output);
      values.put(getColumn(state.getGroupId(), state.getInstanceId()), os.toByteArray());
    }

    store(streamId.toBytes(), values.build());
  }

  @Override
  public final void remove(Iterable<? extends StreamConsumerState> states) throws IOException {
    Set<byte[]> columns = Sets.newTreeSet(Bytes.BYTES_COMPARATOR);
    for (StreamConsumerState state : states) {
      columns.add(getColumn(state.getGroupId(), state.getInstanceId()));
    }
    delete(streamId.toBytes(), columns);
  }

  /**
   * Fetches the cell value for the given row and column.
   * If no such value exists, {@code null} should be returned.
   */
  protected abstract byte[] fetch(byte[] row, byte[] column) throws IOException;

  /**
   * Fetches all values for the given row.
   *
   * @param row the row to fetch from.
   * @param result a map from column to value.
   */
  protected abstract void fetchAll(byte[] row, Map<byte[], byte[]> result) throws IOException;

  /**
   * Fetches all values for the given row. Children can optionally use the columnPrefix to do more efficient retrieval.
   *
   * @param row the row to fetch from.
   * @param columnPrefix the column prefix.
   * @param result a map from column to value. It's valid to contains columns that don't start with columnPrefix.
   */
  protected abstract void fetchAll(byte[] row, byte[] columnPrefix, Map<byte[], byte[]> result) throws IOException;

  /**
   * Stores the given value to cell identified by the given row and column.
   */
  protected abstract void store(byte[] row, byte[] column, byte[] value) throws IOException;

  /**
   * Stores all the values to the given row.
   * @param row the row to store to.
   * @param values Map from column name to value.
   */
  protected abstract void store(byte[] row, Map<byte[], byte[]> values) throws IOException;

  /**
   * Deletes the set of columns from the given row.
   * @param row the row to act on.
   * @param columns columns to get deleted.
   */
  protected abstract void delete(byte[] row, Set<byte[]> columns) throws IOException;

  /**
   * Encodes list of {@link StreamFileOffset} into bytes.
   */
  private byte[] encodeOffsets(Iterable<StreamFileOffset> offsets) throws IOException {
    // Assumption: Each offset encoded into ~40 bytes and there are 8 offsets (number of live files)
    ByteArrayDataOutput output = ByteStreams.newDataOutput(320);
    encodeOffsets(offsets, output);
    return output.toByteArray();
  }

  private void encodeOffsets(Iterable<StreamFileOffset> offsets, DataOutput output) throws IOException {
    for (StreamFileOffset offset : offsets) {
      StreamUtils.encodeOffset(output, offset);
    }
  }

  /**
   * Decodes encoded bytes back to list of {@link StreamFileOffset}.
   */
  private Iterable<StreamFileOffset> decodeOffsets(byte[] encoded) throws IOException {
    ImmutableList.Builder<StreamFileOffset> offsets = ImmutableList.builder();
    if (encoded != null && encoded.length > 0) {
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
      while (input.available() > 0) {
        offsets.add(StreamUtils.decodeOffset(streamConfig, input));
      }
    }
    return offsets.build();
  }

  private byte[] getColumn(long groupId, int instanceId) {
    byte[] column = new byte[Longs.BYTES + Ints.BYTES];
    Bytes.putLong(column, 0, groupId);
    Bytes.putInt(column, Longs.BYTES, instanceId);
    return column;
  }

  /**
   * Decodes the group id from the column name.
   */
  private long getGroupId(byte[] columnName) {
    return Bytes.toLong(columnName);
  }

  /**
   * Decodes the instance id from the column name.
   */
  private int getInstanceId(byte[] columnName) {
    return Bytes.toInt(columnName, Longs.BYTES);
  }
}
