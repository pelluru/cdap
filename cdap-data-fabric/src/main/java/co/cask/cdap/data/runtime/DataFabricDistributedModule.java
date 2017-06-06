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

package co.cask.cdap.data.runtime;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.transaction.DistributedTransactionSystemClientService;
import co.cask.cdap.data2.transaction.TransactionSystemClientService;
import co.cask.cdap.data2.transaction.metrics.TransactionManagerMetricsCollector;
import co.cask.cdap.data2.transaction.queue.QueueAdmin;
import co.cask.cdap.data2.transaction.queue.hbase.HBaseQueueAdmin;
import co.cask.cdap.data2.transaction.queue.hbase.HBaseQueueClientFactory;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.data2.util.hbase.HBaseTableUtilFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.apache.hadoop.conf.Configuration;
import org.apache.tephra.TxConstants;
import org.apache.tephra.distributed.PooledClientProvider;
import org.apache.tephra.distributed.ThreadLocalClientProvider;
import org.apache.tephra.distributed.ThriftClientProvider;
import org.apache.tephra.metrics.TxMetricsCollector;
import org.apache.tephra.runtime.TransactionModules;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines guice bindings for distributed modules.
 */
public class DataFabricDistributedModule extends AbstractModule {
  private static final Logger LOG = LoggerFactory.getLogger(DataFabricDistributedModule.class);
  private final String txClientId;

  public DataFabricDistributedModule(String txClientId) {
    this.txClientId = txClientId;
  }

  @Override
  public void configure() {
    bind(ThriftClientProvider.class).toProvider(ThriftClientProviderSupplier.class);
    bind(QueueClientFactory.class).to(HBaseQueueClientFactory.class).in(Singleton.class);
    bind(QueueAdmin.class).to(HBaseQueueAdmin.class).in(Singleton.class);
    bind(HBaseTableUtil.class).toProvider(HBaseTableUtilFactory.class);

    // bind transactions
    bind(TxMetricsCollector.class).to(TransactionManagerMetricsCollector.class).in(Scopes.SINGLETON);
    bind(TransactionSystemClientService.class).to(DistributedTransactionSystemClientService.class);
    install(new TransactionModules(txClientId).getDistributedModules());
    install(new TransactionExecutorModule());
  }

  /**
   * Provides implementation of {@link ThriftClientProvider} based on configuration.
   */
  @Singleton
  public static final class ThriftClientProviderSupplier implements Provider<ThriftClientProvider> {

    private final CConfiguration cConf;
    private final Configuration hConf;
    private DiscoveryServiceClient discoveryServiceClient;

    @Inject
    ThriftClientProviderSupplier(CConfiguration cConf, Configuration hConf) {
      this.cConf = cConf;
      this.hConf = hConf;
    }

    @Inject(optional = true)
    void setDiscoveryServiceClient(DiscoveryServiceClient discoveryServiceClient) {
      this.discoveryServiceClient = discoveryServiceClient;
    }

    @Override
    public ThriftClientProvider get() {
      // configure the client provider
      String provider = cConf.get(TxConstants.Service.CFG_DATA_TX_CLIENT_PROVIDER,
                                  TxConstants.Service.DEFAULT_DATA_TX_CLIENT_PROVIDER);
      ThriftClientProvider clientProvider;
      if ("pool".equals(provider)) {
        clientProvider = new PooledClientProvider(hConf, discoveryServiceClient);
      } else if ("thread-local".equals(provider)) {
        clientProvider = new ThreadLocalClientProvider(hConf, discoveryServiceClient);
      } else {
        String message = "Unknown Transaction Service Client Provider '" + provider + "'.";
        LOG.error(message);
        throw new IllegalArgumentException(message);
      }
      return clientProvider;
    }
  }
}
