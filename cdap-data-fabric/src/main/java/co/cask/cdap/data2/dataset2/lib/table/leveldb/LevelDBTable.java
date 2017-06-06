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

package co.cask.cdap.data2.dataset2.lib.table.leveldb;

import co.cask.cdap.api.annotation.ReadOnly;
import co.cask.cdap.api.annotation.WriteOnly;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.DataSetException;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.table.Row;
import co.cask.cdap.api.dataset.table.Scan;
import co.cask.cdap.api.dataset.table.Scanner;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.dataset2.lib.table.BufferingTable;
import co.cask.cdap.data2.dataset2.lib.table.FuzzyRowFilter;
import co.cask.cdap.data2.dataset2.lib.table.IncrementValue;
import co.cask.cdap.data2.dataset2.lib.table.PutValue;
import co.cask.cdap.data2.dataset2.lib.table.Update;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.PrefixedNamespaces;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import javax.annotation.Nullable;

/**
 * A table client based on LevelDB.
 */
public class LevelDBTable extends BufferingTable {

  private final LevelDBTableCore core;
  private long persistedVersion;

  public LevelDBTable(DatasetContext datasetContext, String tableName,
                      LevelDBTableService service, CConfiguration cConf,
                      DatasetSpecification spec) throws IOException {
    super(PrefixedNamespaces.namespace(cConf, datasetContext.getNamespaceId(), tableName),
          false, spec.getProperties());
    this.core = new LevelDBTableCore(getTableName(), service);
  }

  @WriteOnly
  @Override
  public void increment(byte[] row, byte[][] columns, long[] amounts) {
    // for local operation with leveldb, we don't worry about the cost of reads
    internalIncrementAndGet(row, columns, amounts);
  }

  @Override
  protected void persist(NavigableMap<byte[], NavigableMap<byte[], Update>> changes) throws Exception {
    persistedVersion = tx == null ? System.currentTimeMillis() : tx.getWritePointer();

    NavigableMap<byte[], NavigableMap<byte[], byte[]>> puts = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    NavigableMap<byte[], NavigableMap<byte[], Long>> increments = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    for (Map.Entry<byte[], NavigableMap<byte[], Update>> rowEntry : changes.entrySet()) {
      for (Map.Entry<byte[], Update> colEntry : rowEntry.getValue().entrySet()) {
        Update val = colEntry.getValue();
        if (val instanceof IncrementValue) {
          NavigableMap<byte[], Long> incrCols = increments.get(rowEntry.getKey());
          if (incrCols == null) {
            incrCols = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
            increments.put(rowEntry.getKey(), incrCols);
          }
          incrCols.put(colEntry.getKey(), ((IncrementValue) val).getValue());
        } else if (val instanceof PutValue) {
          NavigableMap<byte[], byte[]> putCols = puts.get(rowEntry.getKey());
          if (putCols == null) {
            putCols = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
            puts.put(rowEntry.getKey(), putCols);
          }
          putCols.put(colEntry.getKey(), ((PutValue) val).getValue());
        }
      }
    }
    if (!increments.isEmpty() || !puts.isEmpty()) {
      persist(increments, puts);
    }
  }

  @WriteOnly
  private void persist(NavigableMap<byte[], NavigableMap<byte[], Long>> increments,
                       NavigableMap<byte[], NavigableMap<byte[], byte[]>> puts) throws IOException {
    for (Map.Entry<byte[], NavigableMap<byte[], Long>> incEntry : increments.entrySet()) {
      core.increment(incEntry.getKey(), incEntry.getValue());
    }
    core.persist(puts, persistedVersion);
  }

  @Override
  protected void undo(NavigableMap<byte[], NavigableMap<byte[], Update>> persisted) throws Exception {
    if (persisted.isEmpty()) {
      return;
    }
    undoPersisted(persisted);
  }

  @WriteOnly
  private void undoPersisted(NavigableMap<byte[], NavigableMap<byte[], Update>> persisted) throws IOException {
    core.undo(persisted, persistedVersion);
  }

  @ReadOnly
  @Override
  protected NavigableMap<byte[], byte[]> getPersisted(byte[] row, @Nullable byte[][] columns) throws Exception {
    return core.getRow(row, columns, null, null, -1, tx);
  }

  @ReadOnly
  @Override
  protected NavigableMap<byte[], byte[]> getPersisted(byte[] row, byte[] startColumn, byte[] stopColumn, int limit)
    throws Exception {
    return core.getRow(row, null, startColumn, stopColumn, limit, tx);
  }

  @ReadOnly
  @Override
  protected Scanner scanPersisted(Scan scan) throws Exception {

    FuzzyRowFilter filter = null;
    if (scan.getFilter() != null) {
      // todo: currently we support only FuzzyRowFilter as an experimental feature
      if (scan.getFilter() instanceof FuzzyRowFilter) {
        filter = (FuzzyRowFilter) scan.getFilter();
      } else {
        throw new DataSetException("Unknown filter type: " + scan.getFilter());
      }
    }
    final Scanner scanner = core.scan(scan.getStartRow(), scan.getStopRow(), filter, null, tx);
    return new Scanner() {
      @Nullable
      @Override
      public Row next() {
        return LevelDBTable.this.next(scanner);
      }

      @Override
      public void close() {
        scanner.close();
      }
    };
  }

  // Helper methods to help operate on the Scanner with authroization

  @ReadOnly
  private Row next(Scanner scanner) {
    return scanner.next();
  }
}
