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

package co.cask.cdap.metrics.process;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.twill.kafka.client.FetchedMessage;
import org.apache.twill.kafka.client.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link KafkaConsumer.MessageCallback} that persists offset information into a VCTable while
 * delegating the actual message consumption to another {@link KafkaConsumer.MessageCallback}.
 */
public final class PersistedMessageCallback implements KafkaConsumer.MessageCallback {

  private static final Logger LOG = LoggerFactory.getLogger(PersistedMessageCallback.class);

  private final KafkaConsumer.MessageCallback delegate;
  private final MetricsConsumerMetaTable metaTable;
  private final int persistThreshold;
  private final Map<TopicPartitionMetaKey, Long> offsets;
  private final AtomicInteger messageCount;

  /**
   * Constructs a {@link PersistedMessageCallback} which delegates to the given callback for actual action while
   * persisting offsets information in to the given meta table when number of messages has been processed based
   * on the persistThreshold.
   */
  public PersistedMessageCallback(KafkaConsumer.MessageCallback delegate,
                                  MetricsConsumerMetaTable metaTable,
                                  int persistThreshold) {
    this.delegate = delegate;
    this.metaTable = metaTable;
    this.persistThreshold = persistThreshold;
    this.offsets = Maps.newConcurrentMap();
    this.messageCount = new AtomicInteger();
  }

  @Override
  public long onReceived(Iterator<FetchedMessage> messages) {
    long offset = delegate.onReceived(new OffsetTrackingIterator(messages));
    if (messageCount.get() >= persistThreshold) {
      messageCount.set(0);
      persistOffsets();
    }
    return offset;
  }

  @Override
  public void finished() {
    try {
      delegate.finished();
    } finally {
      // Save the offset
      persistOffsets();
    }
  }

  private void persistOffsets() {
    try {
      metaTable.save(ImmutableMap.copyOf(offsets));
    } catch (Exception e) {
      // Simple log and ignore the error.
      LOG.error("Failed to persist consumed message offset. {}", e.getMessage(), e);
    }
  }

  /**
   * Inner help class to track offsets of {@link FetchedMessage} being consumed and
   * persist to meta table when persistThreshold is reached.
   */
  private final class OffsetTrackingIterator implements Iterator<FetchedMessage> {

    private final Iterator<FetchedMessage> delegate;
    private TopicPartitionMetaKey lastTopicPartition;
    private long lastOffset = -1;

    OffsetTrackingIterator(Iterator<FetchedMessage> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public FetchedMessage next() {
      FetchedMessage message = delegate.next();
      lastTopicPartition = new TopicPartitionMetaKey(message.getTopicPartition());
      lastOffset = message.getNextOffset();
      messageCount.incrementAndGet();
      recordOffset();
      return message;
    }

    @Override
    public void remove() {
      delegate.remove();
    }

    private void recordOffset() {
      if (lastOffset >= 0) {
        offsets.put(lastTopicPartition, lastOffset);
      }
    }
  }
}
