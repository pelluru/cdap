/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.messaging.store.hbase;

import co.cask.cdap.api.dataset.lib.AbstractCloseableIterator;
import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.data2.util.hbase.PutBuilder;
import co.cask.cdap.hbase.wd.AbstractRowKeyDistributor;
import co.cask.cdap.hbase.wd.DistributedScanner;
import co.cask.cdap.messaging.MessagingUtils;
import co.cask.cdap.messaging.store.AbstractMessageTable;
import co.cask.cdap.messaging.store.MessageTable;
import co.cask.cdap.messaging.store.RawMessageTableEntry;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * HBase implementation of {@link MessageTable}.
 */
final class HBaseMessageTable extends AbstractMessageTable {
  private static final byte[] PAYLOAD_COL = MessagingUtils.Constants.PAYLOAD_COL;
  private static final byte[] TX_COL = MessagingUtils.Constants.TX_COL;

  private final HBaseTableUtil tableUtil;
  private final byte[] columnFamily;
  private final HTable hTable;
  private final AbstractRowKeyDistributor rowKeyDistributor;
  private final ExecutorService scanExecutor;
  private final int scanCacheRows;
  private final HBaseExceptionHandler exceptionHandler;

  HBaseMessageTable(HBaseTableUtil tableUtil, HTable hTable, byte[] columnFamily,
                    AbstractRowKeyDistributor rowKeyDistributor, ExecutorService scanExecutor, int scanCacheRows,
                    HBaseExceptionHandler exceptionHandler) {
    this.tableUtil = tableUtil;
    this.hTable = hTable;
    this.columnFamily = Arrays.copyOf(columnFamily, columnFamily.length);
    this.rowKeyDistributor = rowKeyDistributor;
    this.scanExecutor = scanExecutor;
    this.scanCacheRows = scanCacheRows;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  protected CloseableIterator<RawMessageTableEntry> read(byte[] startRow, byte[] stopRow) throws IOException {
    Scan scan = tableUtil.buildScan()
      .setStartRow(startRow)
      .setStopRow(stopRow)
      .setCaching(scanCacheRows)
      .build();

    try {
      final ResultScanner scanner = DistributedScanner.create(hTable, scan, rowKeyDistributor, scanExecutor);
      final RawMessageTableEntry tableEntry = new RawMessageTableEntry();
      return new AbstractCloseableIterator<RawMessageTableEntry>() {
        private boolean closed = false;

        @Override
        protected RawMessageTableEntry computeNext() {
          if (closed) {
            return endOfData();
          }

          Result result;
          try {
            result = scanner.next();
          } catch (IOException e) {
            throw exceptionHandler.handleAndWrap(e);
          }
          if (result == null) {
            return endOfData();
          }

          return tableEntry.set(rowKeyDistributor.getOriginalKey(result.getRow()),
                                result.getValue(columnFamily, TX_COL),
                                result.getValue(columnFamily, PAYLOAD_COL));
        }

        @Override
        public void close() {
          try {
            scanner.close();
          } finally {
            endOfData();
            closed = true;
          }
        }
      };
    } catch (IOException e) {
      throw exceptionHandler.handle(e);
    }
  }

  @Override
  protected void persist(Iterator<RawMessageTableEntry> entries) throws IOException {
    List<Put> batchPuts = new ArrayList<>();
    while (entries.hasNext()) {
      RawMessageTableEntry entry = entries.next();
      PutBuilder putBuilder = tableUtil.buildPut(rowKeyDistributor.getDistributedKey(entry.getKey()));
      if (entry.getTxPtr() != null) {
        putBuilder.add(columnFamily, TX_COL, entry.getTxPtr());
      }

      if (entry.getPayload() != null) {
        putBuilder.add(columnFamily, PAYLOAD_COL, entry.getPayload());
      }
      batchPuts.add(putBuilder.build());
    }

    try {
      if (!batchPuts.isEmpty()) {
        hTable.put(batchPuts);
        if (!hTable.isAutoFlush()) {
          hTable.flushCommits();
        }
      }
    } catch (IOException e) {
      throw exceptionHandler.handle(e);
    }
  }

  @Override
  public void rollback(byte[] startKey, byte[] stopKey, byte[] txWritePtr) throws IOException {
    Scan scan = tableUtil.buildScan()
      .setStartRow(startKey)
      .setStopRow(stopKey)
      .setCaching(scanCacheRows)
      .build();

    List<Put> batchPuts = new ArrayList<>();
    try (ResultScanner scanner = DistributedScanner.create(hTable, scan, rowKeyDistributor, scanExecutor)) {
      for (Result result : scanner) {
        // No need to turn the key back to the original row key because we want to put with the actual row key
        PutBuilder putBuilder = tableUtil.buildPut(result.getRow());
        putBuilder.add(columnFamily, TX_COL, txWritePtr);
        batchPuts.add(putBuilder.build());
      }
    }

    try {
      if (!batchPuts.isEmpty()) {
        hTable.put(batchPuts);
        if (!hTable.isAutoFlush()) {
          hTable.flushCommits();
        }
      }
    } catch (IOException e) {
      throw exceptionHandler.handle(e);
    }
  }

  @Override
  public void close() throws IOException {
    hTable.close();
  }
}
