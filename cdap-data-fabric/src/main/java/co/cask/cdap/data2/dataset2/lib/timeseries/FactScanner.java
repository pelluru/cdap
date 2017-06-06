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

package co.cask.cdap.data2.dataset2.lib.timeseries;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.cube.DimensionValue;
import co.cask.cdap.api.dataset.lib.cube.TimeValue;
import co.cask.cdap.api.dataset.table.Row;
import co.cask.cdap.api.dataset.table.Scanner;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans facts in a {@link FactTable}.
 */
public final class FactScanner implements Iterator<FactScanResult> {

  private final FactCodec codec;

  private final Scanner scanner;
  private final long startTs;
  private final long endTs;

  // Track the number of row scanned through the iterator. It's for reporting and debugging purpose.
  private int rowScanned;

  // Use an internal iterator to avoid leaking AbstractIterator methods to outside.
  private final Iterator<FactScanResult> internalIterator;

  // set of measureNames - useful to process measures that are requested while scanning.
  private final Set<String> measureNames;

  /**
   * Construct a FactScanner. Should only be called by FactTable.
   */
  FactScanner(Scanner scanner, FactCodec codec, long startTs, long endTs, Collection<String> measureNames) {
    this.scanner = scanner;
    this.codec = codec;
    this.internalIterator = createIterator();
    this.startTs = startTs;
    this.endTs = endTs;
    this.measureNames = ImmutableSet.copyOf(measureNames);
  }

  public void close() {
    scanner.close();
  }

  public int getRowScanned() {
    return rowScanned;
  }

  @Override
  public boolean hasNext() {
    return internalIterator.hasNext();
  }

  @Override
  public FactScanResult next() {
    return internalIterator.next();
  }

  @Override
  public void remove() {
    internalIterator.remove();
  }

  private Iterator<FactScanResult> createIterator() {
    return new AbstractIterator<FactScanResult>() {
      @Override
      protected FactScanResult computeNext() {
        Row rowResult;
        while ((rowResult = scanner.next()) != null) {
          rowScanned++;
          byte[] rowKey = rowResult.getRow();

          // Decode context and metric from key
          String measureName = codec.getMeasureName(rowKey);
          // if measureNames is empty we include all metrics
          if (!measureNames.isEmpty() && !measureNames.contains(measureName)) {
            continue;
          }
          // todo: codec.getDimensionValues(rowKey) needs to un-encode dimension names which may result in read in
          //       entity table (depending on the cache and its state). To avoid that, we can pass to scanner the
          //       list of dimension names as we *always* know it (it is given) at the time of scanning
          List<DimensionValue> dimensionValues = codec.getDimensionValues(rowKey);

          boolean exhausted = false;
          List<TimeValue> timeValues = Lists.newLinkedList();
          // todo: entry set is ordered by ts?
          for (Map.Entry<byte[], byte[]> columnValue : rowResult.getColumns().entrySet()) {
            long ts = codec.getTimestamp(rowKey, columnValue.getKey());
            if (ts < startTs) {
              continue;
            }

            if (ts > endTs) {
              exhausted = true;
              break;
            }

            // todo: move Bytes.toLong into codec?
            TimeValue timeValue = new TimeValue(ts, Bytes.toLong(columnValue.getValue()));
            timeValues.add(timeValue);
          }

          if (timeValues.isEmpty() && exhausted) {
            break;
          }

          // todo: can return empty list, if all data is < startTs or > endTs
          return new FactScanResult(measureName, dimensionValues, timeValues);
        }

        scanner.close();
        return endOfData();
      }
    };
  }
}
