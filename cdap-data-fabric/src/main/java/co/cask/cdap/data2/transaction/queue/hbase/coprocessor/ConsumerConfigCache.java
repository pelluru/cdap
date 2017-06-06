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

package co.cask.cdap.data2.transaction.queue.hbase.coprocessor;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.transaction.queue.QueueConstants;
import co.cask.cdap.data2.transaction.queue.QueueEntryRow;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.AbstractIdleService;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.tephra.Transaction;
import org.apache.tephra.TransactionCodec;
import org.apache.tephra.TxConstants;
import org.apache.tephra.persist.TransactionSnapshot;
import org.apache.tephra.persist.TransactionVisibilityState;
import org.apache.tephra.util.TxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Provides a RegionServer shared cache for all instances of {@code HBaseQueueRegionObserver} of the recent
 * queue consumer configuration.
 */
public class ConsumerConfigCache extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(ConsumerConfigCache.class);

  // Number of bytes for consumer state column (groupId + instanceId)
  private static final int STATE_COLUMN_SIZE = Bytes.SIZEOF_LONG + Bytes.SIZEOF_INT;
  // update interval for CConfiguration
  private static final long CONFIG_UPDATE_FREQUENCY = 300 * 1000L;

  private final TableName queueConfigTableName;
  private final CConfigurationReader cConfReader;
  private final Supplier<TransactionVisibilityState> transactionSnapshotSupplier;
  private final InputSupplier<HTableInterface> hTableSupplier;
  private final TransactionCodec txCodec;

  private volatile Thread refreshThread;
  private volatile boolean stopped;
  private volatile Map<byte[], QueueConsumerConfig> configCache = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
  private volatile CConfiguration conf;

  private long lastUpdated;
  private long configCacheUpdateFrequency = QueueConstants.DEFAULT_QUEUE_CONFIG_UPDATE_FREQUENCY;
  // timestamp of the last update from the configuration table
  private long lastConfigUpdate;

  /**
   * Constructs a new instance.
   *
   * @param queueConfigTableName table name that stores queue configuration
   * @param cConfReader reader to read the latest {@link CConfiguration}
   * @param transactionSnapshotSupplier A supplier for the latest {@link TransactionSnapshot}
   * @param hTableSupplier A supplier for creating {@link HTableInterface}.
   */
  ConsumerConfigCache(TableName queueConfigTableName, CConfigurationReader cConfReader,
                      Supplier<TransactionVisibilityState> transactionSnapshotSupplier,
                      InputSupplier<HTableInterface> hTableSupplier) {
    this.queueConfigTableName = queueConfigTableName;
    this.cConfReader = cConfReader;
    this.transactionSnapshotSupplier = transactionSnapshotSupplier;
    this.hTableSupplier = hTableSupplier;
    this.txCodec = new TransactionCodec();
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting ConsumerConfigCache Refresh Thread for Table : {}", queueConfigTableName);
    startRefreshThread();
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Stopping ConsumerConfigCache Refresh Thread for Table : {}", queueConfigTableName);
    stopped = true;
    if (refreshThread != null) {
      refreshThread.interrupt();
      refreshThread.join(TimeUnit.SECONDS.toMillis(1));
    }
  }

  public boolean isAlive() {
    return refreshThread.isAlive();
  }

  @Nullable
  public CConfiguration getCConf() {
    return (conf != null) ? CConfiguration.copy(conf) : null;
  }

  @Nullable
  public QueueConsumerConfig getConsumerConfig(byte[] queueName) {
    return configCache.get(queueName);
  }

  private void updateConfig() {
    long now = System.currentTimeMillis();
    if (this.conf == null || now > (lastConfigUpdate + CONFIG_UPDATE_FREQUENCY)) {
      try {
        CConfiguration conf = cConfReader.read();
        if (conf != null) {
          this.conf = conf;
          LOG.info("Reloaded CConfiguration at {}", now);
          this.lastConfigUpdate = now;
          long configUpdateFrequency = conf.getLong(QueueConstants.QUEUE_CONFIG_UPDATE_FREQUENCY,
                                                    QueueConstants.DEFAULT_QUEUE_CONFIG_UPDATE_FREQUENCY);
          LOG.info("Will reload consumer config cache every {} seconds", configUpdateFrequency);
          this.configCacheUpdateFrequency = configUpdateFrequency * 1000;
        }
      } catch (IOException ioe) {
        LOG.error("Error reading default configuration table", ioe);
      }
    }
  }

  /**
   * This forces an immediate update of the config cache. It should only be called from the refresh thread or from
   * tests, to avoid having to add a sleep for the duration of the refresh interval.
   *
   * This method is synchronized to protect from race conditions if called directly from a test. Otherwise this is
   * only called from the refresh thread, and there will not be concurrent invocations.
   *
   * @throws IOException if failed to update config cache
   */
  @VisibleForTesting
  public synchronized void updateCache() throws IOException {
    Map<byte[], QueueConsumerConfig> newCache = Maps.newTreeMap(Bytes.BYTES_COMPARATOR);
    long now = System.currentTimeMillis();
    TransactionVisibilityState txSnapshot = transactionSnapshotSupplier.get();
    if (txSnapshot == null) {
      LOG.debug("No transaction snapshot is available. Not updating the consumer config cache.");
      return;
    }

    HTableInterface table = hTableSupplier.getInput();
    try {
      // Scan the table with the transaction snapshot
      Scan scan = new Scan();
      scan.addFamily(QueueEntryRow.COLUMN_FAMILY);
      Transaction tx = TxUtils.createDummyTransaction(txSnapshot);
      setScanAttribute(scan, TxConstants.TX_OPERATION_ATTRIBUTE_KEY, txCodec.encode(tx));
      ResultScanner scanner = table.getScanner(scan);
      int configCnt = 0;
      for (Result result : scanner) {
        if (!result.isEmpty()) {
          NavigableMap<byte[], byte[]> familyMap = result.getFamilyMap(QueueEntryRow.COLUMN_FAMILY);
          if (familyMap != null) {
            configCnt++;
            Map<ConsumerInstance, byte[]> consumerInstances = new HashMap<>();
            // Gather the startRow of all instances across all consumer groups.
            int numGroups = 0;
            Long groupId = null;
            for (Map.Entry<byte[], byte[]> entry : familyMap.entrySet()) {
              if (entry.getKey().length != STATE_COLUMN_SIZE) {
                continue;
              }
              long gid = Bytes.toLong(entry.getKey());
              int instanceId = Bytes.toInt(entry.getKey(), Bytes.SIZEOF_LONG);
              consumerInstances.put(new ConsumerInstance(gid, instanceId), entry.getValue());

              // Columns are sorted by groupId, hence if it change, then numGroups would get +1
              if (groupId == null || groupId != gid) {
                numGroups++;
                groupId = gid;
              }
            }
            byte[] queueName = result.getRow();
            newCache.put(queueName, new QueueConsumerConfig(consumerInstances, numGroups));
          }
        }
      }
      long elapsed = System.currentTimeMillis() - now;
      this.configCache = newCache;
      this.lastUpdated = now;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Updated consumer config cache with {} entries, took {} msec", configCnt, elapsed);
      }
    } finally {
      try {
        table.close();
      } catch (IOException ioe) {
        LOG.error("Error closing table {}", queueConfigTableName, ioe);
      }
    }
  }

  private void startRefreshThread() {
    refreshThread = new Thread("queue-cache-refresh") {
      @Override
      public void run() {
        while (!isInterrupted() && !stopped) {
          updateConfig();
          long now = System.currentTimeMillis();
          if (now > (lastUpdated + configCacheUpdateFrequency)) {
            try {
              updateCache();
            } catch (TableNotFoundException e) {
              // This is expected when the namespace goes away since there is one config table per namespace
              // If the table is not found due to other situation, the region observer already
              // has logic to get a new one through the getInstance method
              LOG.warn("Queue config table not found: {}", queueConfigTableName, e);
              break;
            } catch (IOException e) {
              LOG.warn("Error updating queue consumer config cache", e);
            }
          }
          try {
            TimeUnit.SECONDS.sleep(1);
          } catch (InterruptedException ie) {
            // reset status
            interrupt();
            break;
          }
        }
        LOG.info("Config cache update for {} terminated.", queueConfigTableName);
      }
    };
    refreshThread.setDaemon(true);
    refreshThread.start();
  }

  /**
   * Sets an attribute to the given {@link Scan} object. Instead of calling {@link Scan#setAttribute(String, byte[])}
   * directly, it uses reflection to call the method. This is because the return type for the setAttribute method
   * is different in different HBase version.
   */
  private void setScanAttribute(Scan scan, String name, byte[] value) {
    try {
      Method setAttribute = scan.getClass().getMethod("setAttribute", String.class, byte[].class);
      setAttribute.invoke(scan, name, value);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      LOG.error("Failed to call Scan.setAttribute", e);
      throw Throwables.propagate(e);
    }
  }
}
