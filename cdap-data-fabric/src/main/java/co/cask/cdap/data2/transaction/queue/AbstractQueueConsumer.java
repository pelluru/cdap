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
package co.cask.cdap.data2.transaction.queue;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.common.utils.ImmutablePair;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.queue.DequeueResult;
import co.cask.cdap.data2.queue.DequeueStrategy;
import co.cask.cdap.data2.queue.QueueConsumer;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.apache.tephra.Transaction;
import org.apache.tephra.TransactionAware;
import org.apache.tephra.TxConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Common queue consumer for persisting engines such as HBase and LevelDB.
 */
public abstract class AbstractQueueConsumer implements QueueConsumer, TransactionAware {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractQueueConsumer.class);
  private static final DequeueResult<byte[]> EMPTY_RESULT = DequeueResult.Empty.result();

  // TODO: Make these configurable.
  // Minimum number of rows to fetch per scan.
  private static final int MIN_FETCH_ROWS = 100;
  // Multiple of batches to fetch per scan.
  // Number of rows to scan = max(MIN_FETCH_ROWS, dequeueBatchSize * groupSize * PREFETCH_BATCHES)
  private static final int PREFETCH_BATCHES = 10;

  private static final Function<SimpleQueueEntry, byte[]> ENTRY_TO_BYTE_ARRAY =
    new Function<SimpleQueueEntry, byte[]>() {
      @Override
      public byte[] apply(SimpleQueueEntry input) {
        return input.getData();
      }
    };

  protected final byte[] stateColumnName;
  private final ConsumerConfig consumerConfig;
  private final QueueName queueName;
  private final SortedMap<byte[], SimpleQueueEntry> entryCache;
  private final NavigableMap<byte[], SimpleQueueEntry> consumingEntries;
  private final byte[] queueRowPrefix;

  // Maximum amount of time spent in dequeue to avoid transaction timeout.
  private final long maxDequeueMillis;

  private byte[] scanStartRow;
  private boolean committed;
  protected Transaction transaction;
  protected int commitCount;

  protected abstract boolean claimEntry(byte[] rowKey, byte[] stateContent) throws IOException;
  protected abstract void updateState(Set<byte[]> rowKeys, byte[] stateColumnName, byte[] stateContent)
    throws IOException;
  protected abstract void undoState(Set<byte[]> rowKeys, byte[] stateColumnName)
    throws IOException, InterruptedException;
  protected abstract QueueScanner getScanner(byte[] startRow, byte[] stopRow, int numRows) throws IOException;

  protected AbstractQueueConsumer(CConfiguration cConf, ConsumerConfig consumerConfig, QueueName queueName) {
    this(cConf, consumerConfig, queueName, null);
  }

  protected AbstractQueueConsumer(CConfiguration cConf, ConsumerConfig consumerConfig,
                                  QueueName queueName, @Nullable byte[] startRow) {
    this.consumerConfig = consumerConfig;
    this.queueName = queueName;
    this.entryCache = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    this.consumingEntries = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    this.queueRowPrefix = QueueEntryRow.getQueueRowPrefix(queueName);
    this.scanStartRow = (startRow == null || startRow.length == 0)
                        ? QueueEntryRow.getQueueEntryRowKey(queueName, 0L, 0) : startRow;
    this.stateColumnName = Bytes.add(QueueEntryRow.STATE_COLUMN_PREFIX,
                                     Bytes.toBytes(consumerConfig.getGroupId()));

    // Maximum time to spend in dequeue.
    int dequeuePercent = cConf.getInt(QueueConstants.ConfigKeys.DEQUEUE_TX_PERCENT);
    Preconditions.checkArgument(dequeuePercent > 0 && dequeuePercent <= 100,
                                "Invalid value for %s", QueueConstants.ConfigKeys.DEQUEUE_TX_PERCENT);
    long txTimeout = TimeUnit.SECONDS.toMillis(cConf.getLong(TxConstants.Manager.CFG_TX_TIMEOUT));
    this.maxDequeueMillis = txTimeout * dequeuePercent / 100;
  }

  @Override
  public QueueName getQueueName() {
    return queueName;
  }

  @Override
  public ConsumerConfig getConfig() {
    return consumerConfig;
  }

  @Override
  public DequeueResult<byte[]> dequeue() throws IOException {
    return dequeue(1);
  }

  @Override
  public DequeueResult<byte[]> dequeue(int maxBatchSize) throws IOException {
    DequeueResult<byte[]> result = performDequeue(maxBatchSize);
    // Start row can be updated to the largest rowKey in the consumingEntries
    // that is smaller than or equal to scanStartRow. If no such key exists, update start row to scanStartRow
    byte[] floorKey = consumingEntries.floorKey(scanStartRow);
    updateStartRow(floorKey == null ? scanStartRow : floorKey);

    return result;
  }

  @Override
  public void startTx(Transaction tx) {
    consumingEntries.clear();
    this.transaction = tx;
    this.committed = false;
  }

  @Override
  public void updateTx(Transaction transaction) {
    this.transaction = transaction;
  }

  @Override
  public Collection<byte[]> getTxChanges() {
    // No conflicts guaranteed in dequeue logic.
    return ImmutableList.of();
  }

  @Override
  public boolean commitTx() throws Exception {
    if (consumingEntries.isEmpty()) {
      return true;
    }

    byte[] stateContent = encodeStateColumn(ConsumerEntryState.PROCESSED);
    updateState(consumingEntries.keySet(), stateColumnName, stateContent);
    commitCount += consumingEntries.size();
    committed = true;
    return true;
  }

  @Override
  public boolean rollbackTx() throws Exception {
    if (consumingEntries.isEmpty()) {
      return true;
    }

    // Put the consuming entries back to cache
    entryCache.putAll(consumingEntries);

    // If not committed, no need to update HBase.
    if (!committed) {
      return true;
    }
    commitCount -= consumingEntries.size();

    // Revert changes in HBase rows
    // If it is FIFO, restore to the CLAIMED state. This instance will retry it on the next dequeue.
    if (getConfig().getDequeueStrategy() == DequeueStrategy.FIFO && getConfig().getGroupSize() > 1) {
      byte[] stateContent = encodeStateColumn(ConsumerEntryState.CLAIMED);
      updateState(consumingEntries.keySet(), stateColumnName, stateContent);
    } else {
      undoState(consumingEntries.keySet(), stateColumnName);
    }
    return true;
  }

  /**
   * Called when the start row is updated.
   */
  protected void updateStartRow(byte[] startRow) {
    // No-op by default.
  }

  private DequeueResult<byte[]> performDequeue(int maxBatchSize) throws IOException {
    Preconditions.checkArgument(maxBatchSize > 0, "Batch size must be > 0.");

    // pre-compute the "claimed" state content in case of FIFO.
    byte[] claimedStateValue = null;
    if (getConfig().getDequeueStrategy() == DequeueStrategy.FIFO && getConfig().getGroupSize() > 1) {
      claimedStateValue = encodeStateColumn(ConsumerEntryState.CLAIMED);
    }

    boolean isReachedDequeueTimeLimit = false;
    Stopwatch stopwatch = new Stopwatch();
    stopwatch.start();
    while (consumingEntries.size() < maxBatchSize && getEntries(consumingEntries, maxBatchSize, stopwatch)) {

      // ANDREAS: this while loop should stop once getEntries/populateCache reaches the end of the queue. Currently, it
      // will retry as long as it gets at least one entry in every round, even if that is an entry that must be ignored
      // because it cannot be claimed.
      // ANDREAS: It could be a problem that we always read to the end of the queue. This way one flowlet instance may
      // always all entries, while others are idle.

      // For FIFO, need to try claiming the entry if group size > 1
      if (getConfig().getDequeueStrategy() == DequeueStrategy.FIFO && getConfig().getGroupSize() > 1) {
        Iterator<Map.Entry<byte[], SimpleQueueEntry>> iterator = consumingEntries.entrySet().iterator();
        while (iterator.hasNext()) {
          SimpleQueueEntry entry = iterator.next().getValue();

          if (entry.getState() == null ||
            QueueEntryRow.getStateInstanceId(entry.getState()) >= getConfig().getGroupSize()) {
            // If not able to claim it, remove it, and move to next one.
            if (!claimEntry(entry.getRowKey(), claimedStateValue)) {
              iterator.remove();
            }

            if (stopwatch.elapsedMillis() >= maxDequeueMillis) {
              break;
            }
          }
        }
        // Drain the iterator in case of dequeue time limit reached
        Iterators.advance(iterator, Integer.MAX_VALUE);
      }

      if (stopwatch.elapsedMillis() >= maxDequeueMillis) {
        // If time limit reached and yet we don't have enough entries as requested, treat it as dequeue time limit
        // reached. There can be some false positive (reached the end of queue, yet passed the time limit), but
        // it's ok since we only use this boolean for logging only and normally it won't be the case as long as
        // dequeue is completed in relatively short time comparing to the tx timeout.
        isReachedDequeueTimeLimit = consumingEntries.size() < maxBatchSize;
        break;
      }
    }

    // If nothing get dequeued, return the empty result.
    if (consumingEntries.isEmpty()) {
      if (isReachedDequeueTimeLimit) {
        LOG.warn("Unable to dequeue any entry after {}ms.", maxDequeueMillis);
      }
      return EMPTY_RESULT;
    }

    if (isReachedDequeueTimeLimit) {
      LOG.warn("Dequeue time limit of {}ms reached. Requested batch size {}, dequeued {}",
               maxDequeueMillis, maxBatchSize, consumingEntries.size());
    }

    return new SimpleDequeueResult(consumingEntries.values());
  }

  /**
   * Try to dequeue (claim) entries up to a maximum size.
   * @param entries For claimed entries to fill in.
   * @param maxBatchSize Maximum number of entries to claim.
   * @return The entries instance.
   * @throws java.io.IOException
   */
  private boolean getEntries(SortedMap<byte[], SimpleQueueEntry> entries,
                             int maxBatchSize, Stopwatch stopwatch) throws IOException {
    boolean hasEntry = fetchFromCache(entries, maxBatchSize);

    // If not enough entries from the cache, try to get more.
    if (entries.size() < maxBatchSize) {
      populateRowCache(entries.keySet(), maxBatchSize, stopwatch);
      hasEntry = fetchFromCache(entries, maxBatchSize) || hasEntry;
    }

    return hasEntry;
  }

  private boolean fetchFromCache(SortedMap<byte[], SimpleQueueEntry> entries, int maxBatchSize) {
    if (entryCache.isEmpty()) {
      return false;
    }

    Iterator<Map.Entry<byte[], SimpleQueueEntry>> iterator = entryCache.entrySet().iterator();
    while (entries.size() < maxBatchSize && iterator.hasNext()) {
      Map.Entry<byte[], SimpleQueueEntry> entry = iterator.next();
      entries.put(entry.getKey(), entry.getValue());
      iterator.remove();
    }
    return true;
  }

  private void populateRowCache(Set<byte[]> excludeRows, int maxBatchSize, Stopwatch stopwatch) throws IOException {

    long readPointer = transaction.getReadPointer();

    // Scan the table for queue entries.
    int numRows = Math.max(MIN_FETCH_ROWS, maxBatchSize * PREFETCH_BATCHES);
    QueueScanner scanner = getScanner(scanStartRow,
                                      QueueEntryRow.getStopRowForTransaction(queueRowPrefix, transaction),
                                      numRows);
    try {
      // Try fill up the cache
      boolean firstScannedRow = true;
      while (entryCache.size() < numRows) {
        ImmutablePair<byte[], Map<byte[], byte[]>> entry = scanner.next();
        if (entry == null) {
          // No more result, breaking out.
          break;
        }

        byte[] rowKey = entry.getFirst();
        if (excludeRows.contains(rowKey)) {
          continue;
        }

        // Row key is queue_name + writePointer + counter
        long writePointer = QueueEntryRow.getWritePointer(rowKey, queueRowPrefix.length);

        // If it is first row returned by the scanner and was written before the earliest in progress,
        // it's safe to advance scanStartRow to current row because nothing can be written before this row.
        if (firstScannedRow && writePointer < transaction.getFirstInProgress()) {
          firstScannedRow = false;
          scanStartRow = Arrays.copyOf(rowKey, rowKey.length);
        }

        // If writes later than the reader pointer, abort the loop, as entries that comes later are all uncommitted.
        // this is probably not needed due to the limit of the scan to the stop row, but to be safe...
        if (writePointer > readPointer) {
          break;
        }
        // If the write is in the excluded list, ignore it.
        if (transaction.isExcluded(writePointer)) {
          continue;
        }

        // Based on the strategy to determine if include the given entry or not.
        byte[] dataBytes = entry.getSecond().get(QueueEntryRow.DATA_COLUMN);
        byte[] metaBytes = entry.getSecond().get(QueueEntryRow.META_COLUMN);

        if (dataBytes == null || metaBytes == null) {
          continue;
        }

        byte[] stateBytes = entry.getSecond().get(stateColumnName);

        int counter = Bytes.toInt(rowKey, rowKey.length - 4, Ints.BYTES);
        if (!shouldInclude(writePointer, counter, metaBytes, stateBytes)) {
          continue;
        }

        entryCache.put(rowKey, new SimpleQueueEntry(rowKey, dataBytes, stateBytes));

        // Check here to make sure there is at least one entry read to make sure there is some progress
        if (stopwatch.elapsedMillis() >= maxDequeueMillis) {
          break;
        }
      }
    } finally {
      scanner.close();
    }
  }

  private byte[] encodeStateColumn(ConsumerEntryState state) {
    // State column content is encoded as (writePointer) + (instanceId) + (state)
    byte[] stateContent = new byte[Longs.BYTES + Ints.BYTES + 1];
    Bytes.putLong(stateContent, 0, transaction.getWritePointer());
    Bytes.putInt(stateContent, Longs.BYTES, getConfig().getInstanceId());
    Bytes.putByte(stateContent, Longs.BYTES + Ints.BYTES, state.getState());
    return stateContent;
  }

  private boolean shouldInclude(long enqueueWritePointer, int counter,
                                byte[] metaValue, byte[] stateValue) throws IOException {

    QueueEntryRow.CanConsume canConsume =
      QueueEntryRow.canConsume(getConfig(), transaction, enqueueWritePointer, counter, metaValue, stateValue);

    if (QueueEntryRow.CanConsume.NO_INCLUDING_ALL_OLDER == canConsume) {
      scanStartRow = getNextRow(scanStartRow, enqueueWritePointer, counter);
      return false;
    }

    return QueueEntryRow.CanConsume.YES == canConsume;
  }

  /**
   * Get the next row based on the given write pointer and counter. It modifies the given row byte[] in place
   * and returns it.
   */
  private byte[] getNextRow(byte[] row, long writePointer, int count) {
    Bytes.putLong(row, queueRowPrefix.length, writePointer);
    Bytes.putInt(row, queueRowPrefix.length + Longs.BYTES, count + 1);
    return row;
  }

  @Override
  public String getTransactionAwareName() {
    return getClass().getSimpleName() + "(queue = " + queueName + ")";
  }

  /**
   * Implementation of dequeue result.
   */
  private final class SimpleDequeueResult implements DequeueResult<byte[]> {

    private final List<SimpleQueueEntry> entries;

    private SimpleDequeueResult(Iterable<SimpleQueueEntry> entries) {
      this.entries = ImmutableList.copyOf(entries);
    }

    @Override
    public boolean isEmpty() {
      return entries.isEmpty();
    }

    @Override
    public void reclaim() {
      // Simply put all entries into consumingEntries and clear those up from the entry cache as well.
      for (SimpleQueueEntry entry : entries) {
        consumingEntries.put(entry.getRowKey(), entry);
        entryCache.remove(entry.getRowKey());
      }
    }

    @Override
    public int size() {
      return entries.size();
    }

    @Override
    public Iterator<byte[]> iterator() {
      if (isEmpty()) {
        return Iterators.emptyIterator();
      }
      return Iterators.transform(entries.iterator(), ENTRY_TO_BYTE_ARRAY);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("size", entries.size())
        .add("queue", queueName)
        .add("config", getConfig())
        .toString();
    }
  }
}
