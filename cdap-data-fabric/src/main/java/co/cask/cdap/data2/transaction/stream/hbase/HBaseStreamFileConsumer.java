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
package co.cask.cdap.data2.transaction.stream.hbase;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data.file.FileReader;
import co.cask.cdap.data.file.ReadFilter;
import co.cask.cdap.data.stream.StreamEventOffset;
import co.cask.cdap.data.stream.StreamFileOffset;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.transaction.queue.QueueEntryRow;
import co.cask.cdap.data2.transaction.stream.AbstractStreamFileConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.data2.transaction.stream.StreamConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConsumerState;
import co.cask.cdap.data2.transaction.stream.StreamConsumerStateStore;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.hbase.wd.AbstractRowKeyDistributor;
import co.cask.cdap.hbase.wd.DistributedScanner;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Threads;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A {@link StreamConsumer} that uses HTable to store consuming states.
 */
@NotThreadSafe
public final class HBaseStreamFileConsumer extends AbstractStreamFileConsumer {

  private final HBaseTableUtil tableUtil;
  private final HTable hTable;
  private final AbstractRowKeyDistributor keyDistributor;
  private final ExecutorService scanExecutor;

  /**
   * Constructor.
   */
  public HBaseStreamFileConsumer(CConfiguration cConf, StreamConfig streamConfig,
                                 ConsumerConfig consumerConfig, HBaseTableUtil tableUtil, HTable hTable,
                                 FileReader<StreamEventOffset, Iterable<StreamFileOffset>> reader,
                                 StreamConsumerStateStore stateStore, StreamConsumerState beginConsumerState,
                                 @Nullable ReadFilter extraFilter,
                                 AbstractRowKeyDistributor keyDistributor) {
    super(cConf, streamConfig, consumerConfig, reader, stateStore, beginConsumerState, extraFilter);
    this.tableUtil = tableUtil;
    this.hTable = hTable;
    this.keyDistributor = keyDistributor;
    this.scanExecutor = createScanExecutor(streamConfig.getStreamId());
  }

  @Override
  protected void doClose() throws IOException {
    scanExecutor.shutdownNow();
    hTable.close();
  }

  @Override
  protected boolean claimFifoEntry(byte[] row, byte[] value, byte[] oldValue) throws IOException {
    Put put = new Put(keyDistributor.getDistributedKey(row));
    put.add(QueueEntryRow.COLUMN_FAMILY, stateColumnName, value);
    return hTable.checkAndPut(put.getRow(), QueueEntryRow.COLUMN_FAMILY, stateColumnName, oldValue, put);
  }

  @Override
  protected void updateState(Iterable<byte[]> rows, int size, byte[] value) throws IOException {
    List<Put> puts = Lists.newArrayListWithCapacity(size);

    for (byte[] row : rows) {
      Put put = new Put(keyDistributor.getDistributedKey(row));
      put.add(QueueEntryRow.COLUMN_FAMILY, stateColumnName, value);
      puts.add(put);
    }
    hTable.put(puts);
    hTable.flushCommits();
  }

  @Override
  protected void undoState(Iterable<byte[]> rows, int size) throws IOException {
    List<Delete> deletes = Lists.newArrayListWithCapacity(size);
    for (byte[] row : rows) {
      Delete delete = new Delete(keyDistributor.getDistributedKey(row));
      delete.deleteColumns(QueueEntryRow.COLUMN_FAMILY, stateColumnName);
      deletes.add(delete);
    }
    hTable.delete(deletes);
    hTable.flushCommits();
  }

  @Override
  protected StateScanner scanStates(byte[] startRow, byte[] stopRow) throws IOException {
    Scan scan = tableUtil.buildScan()
      .setStartRow(startRow)
      .setStopRow(stopRow)
      .setMaxVersions(1)
      .addColumn(QueueEntryRow.COLUMN_FAMILY, stateColumnName)
      .setCaching(MAX_SCAN_ROWS)
      .build();

    // TODO: Add filter for getting committed processed rows only. Need to refactor HBaseQueue2Consumer to extract that.
    final ResultScanner scanner = DistributedScanner.create(hTable, scan, keyDistributor, scanExecutor);
    return new StateScanner() {

      private Result result;

      @Override
      public boolean nextStateRow() throws IOException {
        result = scanner.next();
        return result != null;
      }

      @Override
      public byte[] getRow() {
        return keyDistributor.getOriginalKey(result.getRow());
      }

      @Override
      public byte[] getState() {
        return result.value();
      }

      @Override
      public void close() throws IOException {
        scanner.close();
      }
    };
  }

  private ExecutorService createScanExecutor(StreamId streamId) {
    ThreadFactory threadFactory = Threads.newDaemonThreadFactory(String.format("stream-%s-%s-consumer-scanner-",
                                                                               streamId.getNamespace(),
                                                                               streamId.getEntityName()));
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 20, 60, TimeUnit.SECONDS,
                                                         new SynchronousQueue<Runnable>(), threadFactory);
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}
