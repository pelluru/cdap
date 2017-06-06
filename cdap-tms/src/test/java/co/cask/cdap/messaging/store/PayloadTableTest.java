/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.messaging.store;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.messaging.TopicMetadata;
import co.cask.cdap.messaging.data.MessageId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.TopicId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for Payload Table tests.
 */
public abstract class PayloadTableTest {
  private static final TopicId T1 = NamespaceId.DEFAULT.topic("payloadt1");
  private static final TopicId T2 = NamespaceId.DEFAULT.topic("payloadt2");
  private static final int GENERATION = 1;
  private static final Map<String, String> DEFAULT_PROPERTY = ImmutableMap.of(TopicMetadata.TTL_KEY,
                                                                              Integer.toString(10000),
                                                                              TopicMetadata.GENERATION_KEY,
                                                                              Integer.toString(GENERATION));
  private static final TopicMetadata M1 = new TopicMetadata(T1, DEFAULT_PROPERTY);
  private static final TopicMetadata M2 = new TopicMetadata(T2, DEFAULT_PROPERTY);

  protected abstract PayloadTable getPayloadTable() throws Exception;

  protected abstract MetadataTable getMetadataTable() throws Exception;

  @Test
  public void testSingleMessage() throws Exception {
    TopicId topicId = NamespaceId.DEFAULT.topic("singlePayload");
    TopicMetadata metadata = new TopicMetadata(topicId, DEFAULT_PROPERTY);
    String payload = "data";
    long txWritePtr = 123L;
    try (MetadataTable metadataTable = getMetadataTable();
         PayloadTable table = getPayloadTable()) {
      metadataTable.createTopic(metadata);
      List<PayloadTable.Entry> entryList = new ArrayList<>();
      entryList.add(new TestPayloadEntry(topicId, GENERATION, txWritePtr, 1L, (short) 1, Bytes.toBytes(payload)));
      table.store(entryList.iterator());
      byte[] messageId = new byte[MessageId.RAW_ID_SIZE];
      MessageId.putRawId(0L, (short) 0, 0L, (short) 0, messageId, 0);
      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(metadata, txWritePtr, new MessageId(messageId),
                                                                        false, Integer.MAX_VALUE)) {
        // Fetch not including the first message, expect empty
        Assert.assertFalse(iterator.hasNext());
      }

      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(metadata, txWritePtr, new MessageId(messageId),
                                                                        true, Integer.MAX_VALUE)) {
        // Fetch including the first message
        Assert.assertTrue(iterator.hasNext());
        PayloadTable.Entry entry = iterator.next();
        Assert.assertArrayEquals(Bytes.toBytes(payload), entry.getPayload());
        Assert.assertEquals(txWritePtr, entry.getTransactionWritePointer());
        Assert.assertFalse(iterator.hasNext());
      }
    }
  }

  @Test
  public void testConsumption() throws Exception {
    try (MetadataTable metadataTable = getMetadataTable();
         PayloadTable table = getPayloadTable()) {
      metadataTable.createTopic(M1);
      metadataTable.createTopic(M2);
      List<PayloadTable.Entry> entryList = new ArrayList<>();
      populateList(entryList);
      table.store(entryList.iterator());
      byte[] messageId = new byte[MessageId.RAW_ID_SIZE];
      MessageId.putRawId(0L, (short) 0, 0L, (short) 0, messageId, 0);

      // Fetch data with 100 write pointer
      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(M1, 100, new MessageId(messageId), true,
                                                                   Integer.MAX_VALUE)) {
        checkData(iterator, 123, ImmutableSet.of(100L), 50);
      }

      // Fetch only 10 items with 101 write pointer
      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(M1, 101, new MessageId(messageId), true, 1)) {
        checkData(iterator, 123, ImmutableSet.of(101L), 1);
      }

      // Fetch items with 102 write pointer
      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(M1, 102, new MessageId(messageId), true,
                                                                        Integer.MAX_VALUE)) {
        checkData(iterator, 123, ImmutableSet.of(102L), 50);
      }

      // Fetch from t2 with 101 write pointer
      try (CloseableIterator<PayloadTable.Entry> iterator = table.fetch(M2, 101, new MessageId(messageId), true,
                                                                        Integer.MAX_VALUE)) {
        checkData(iterator, 123, ImmutableSet.of(101L), 50);
      }
    }
  }

  private void checkData(CloseableIterator<PayloadTable.Entry> entries, int payload, Set<Long> acceptablePtrs,
                         int expectedCount) {
    int count = 0;
    while (entries.hasNext()) {
      PayloadTable.Entry entry = entries.next();
      Assert.assertTrue(acceptablePtrs.contains(entry.getTransactionWritePointer()));
      Assert.assertArrayEquals(Bytes.toBytes(payload), entry.getPayload());
      count++;
    }
    Assert.assertEquals(expectedCount, count);
  }

  private void populateList(List<PayloadTable.Entry> payloadTable) {
    List<Integer> writePointers = ImmutableList.of(100, 101, 102);
    int data = 123;

    long timestamp = System.currentTimeMillis();
    short seqId = 0;
    for (Integer writePtr : writePointers) {
      for (int i = 0; i < 50; i++) {
        payloadTable.add(new TestPayloadEntry(T1, GENERATION, writePtr, timestamp, seqId++, Bytes.toBytes(data)));
        payloadTable.add(new TestPayloadEntry(T2, GENERATION, writePtr, timestamp, seqId++, Bytes.toBytes(data)));
      }
    }
  }

  private class TestPayloadEntry implements PayloadTable.Entry {
    private final TopicId topicId;
    private final int generation;
    private final byte[] payload;
    private final long transactionWritePointer;
    private final long writeTimestamp;
    private final short seqId;

    TestPayloadEntry(TopicId topicId, int generation, long transactionWritePointer, long writeTimestamp,
                     short seqId, byte[] payload) {
      this.topicId = topicId;
      this.generation = generation;
      this.transactionWritePointer = transactionWritePointer;
      this.writeTimestamp = writeTimestamp;
      this.seqId = seqId;
      this.payload = payload;
    }

    @Override
    public TopicId getTopicId() {
      return topicId;
    }

    @Override
    public int getGeneration() {
      return generation;
    }

    @Override
    public byte[] getPayload() {
      return payload;
    }

    @Override
    public long getTransactionWritePointer() {
      return transactionWritePointer;
    }

    @Override
    public long getPayloadWriteTimestamp() {
      return writeTimestamp;
    }

    @Override
    public short getPayloadSequenceId() {
      return seqId;
    }
  }
}
