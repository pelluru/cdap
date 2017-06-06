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

import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.transaction.DelegatingTransactionSystemClientService;
import co.cask.cdap.data2.transaction.TransactionSystemClientService;
import co.cask.cdap.data2.transaction.metrics.TransactionManagerMetricsCollector;
import co.cask.cdap.data2.transaction.queue.QueueAdmin;
import co.cask.cdap.data2.transaction.queue.inmemory.InMemoryQueueAdmin;
import co.cask.cdap.data2.transaction.queue.inmemory.InMemoryQueueClientFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.apache.tephra.metrics.TxMetricsCollector;
import org.apache.tephra.runtime.TransactionModules;

/**
 * The Guice module of data fabric bindings for in memory execution.
 */
public class DataFabricInMemoryModule extends AbstractModule {
  private final String txClientId;

  public DataFabricInMemoryModule(String txClientId) {
    this.txClientId = txClientId;
  }

  @Override
  protected void configure() {
    // Bind TxDs2 stuff

    bind(QueueClientFactory.class).to(InMemoryQueueClientFactory.class).in(Singleton.class);
    bind(QueueAdmin.class).to(InMemoryQueueAdmin.class).in(Singleton.class);

    // bind transactions
    bind(TxMetricsCollector.class).to(TransactionManagerMetricsCollector.class).in(Scopes.SINGLETON);
    bind(TransactionSystemClientService.class).to(DelegatingTransactionSystemClientService.class);
    install(new TransactionModules(txClientId).getInMemoryModules());
    install(new TransactionExecutorModule());
  }
}
