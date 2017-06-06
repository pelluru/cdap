/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package co.cask.cdap.data2.dataset2.lib.table.hbase;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.table.TableProperties;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.common.guice.NamespaceClientUnitTestModule;
import co.cask.cdap.common.guice.ZKClientModule;
import co.cask.cdap.data.hbase.HBaseTestBase;
import co.cask.cdap.data.hbase.HBaseTestFactory;
import co.cask.cdap.data.runtime.DataFabricModules;
import co.cask.cdap.data.runtime.DataSetsModules;
import co.cask.cdap.data.runtime.SystemDatasetRuntimeModule;
import co.cask.cdap.data.runtime.TransactionMetricsModule;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.lib.table.MetricsTable;
import co.cask.cdap.data2.dataset2.lib.table.MetricsTableTest;
import co.cask.cdap.data2.util.hbase.HBaseDDLExecutorFactory;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import co.cask.cdap.security.authorization.AuthorizationTestModule;
import co.cask.cdap.security.impersonation.DefaultOwnerAdmin;
import co.cask.cdap.security.impersonation.OwnerAdmin;
import co.cask.cdap.security.impersonation.UGIProvider;
import co.cask.cdap.security.impersonation.UnsupportedUGIProvider;
import co.cask.cdap.spi.hbase.HBaseDDLExecutor;
import co.cask.cdap.test.SlowTests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * metrics table test for HBase.
 */
@Category(SlowTests.class)
public class HBaseMetricsTableTest extends MetricsTableTest {

  @ClassRule
  public static final HBaseTestBase TEST_HBASE = new HBaseTestFactory().get();

  private static HBaseTableUtil tableUtil;
  private static DatasetFramework dsFramework;
  private static HBaseDDLExecutor ddlExecutor;

  @BeforeClass
  public static void setup() throws Exception {
    CConfiguration conf = CConfiguration.create();
    conf.set(Constants.CFG_HDFS_USER, System.getProperty("user.name"));
    Injector injector = Guice.createInjector(new DataFabricModules().getDistributedModules(),
                                             new ConfigModule(conf, TEST_HBASE.getConfiguration()),
                                             new ZKClientModule(),
                                             new DiscoveryRuntimeModule().getDistributedModules(),
                                             new TransactionMetricsModule(),
                                             new LocationRuntimeModule().getDistributedModules(),
                                             new NamespaceClientUnitTestModule().getModule(),
                                             new SystemDatasetRuntimeModule().getDistributedModules(),
                                             new DataSetsModules().getInMemoryModules(),
                                             new AuthorizationTestModule(),
                                             new AuthorizationEnforcementModule().getInMemoryModules(),
                                             new AuthenticationContextModules().getNoOpModule(),
                                             new AbstractModule() {
                                               @Override
                                               protected void configure() {
                                                 bind(UGIProvider.class).to(UnsupportedUGIProvider.class);
                                                 bind(OwnerAdmin.class).to(DefaultOwnerAdmin.class);
                                               }
                                             });

    dsFramework = injector.getInstance(DatasetFramework.class);
    tableUtil = injector.getInstance(HBaseTableUtil.class);
    ddlExecutor = new HBaseDDLExecutorFactory(conf, TEST_HBASE.getHBaseAdmin().getConfiguration()).get();
    ddlExecutor.createNamespaceIfNotExists(tableUtil.getHBaseNamespace(NamespaceId.SYSTEM));
  }

  @AfterClass
  public static void tearDown() throws Exception {
    tableUtil.deleteAllInNamespace(ddlExecutor, tableUtil.getHBaseNamespace(NamespaceId.SYSTEM),
                                   TEST_HBASE.getHBaseAdmin().getConfiguration());
    ddlExecutor.deleteNamespaceIfExists(tableUtil.getHBaseNamespace(NamespaceId.SYSTEM));
  }

  @Override
  @Test
  public void testConcurrentIncrement() throws Exception {
    String testConcurrentIncrement = "testConcurrentIncrement";
    final MetricsTable table = getTable(testConcurrentIncrement);
    final int rounds = 500;
    Map<byte[], Long> inc1 = ImmutableMap.of(X, 1L, Y, 2L);
    Map<byte[], Long> inc2 = ImmutableMap.of(Y, 1L, Z, 2L);
    // HTable used by HBaseMetricsTable is not thread safe, so each thread must use a separate instance
    // HBaseMetricsTable does not support mixed increment and incrementAndGet so the
    // updates and assertions here are different from MetricsTableTest.testConcurrentIncrement()
    Collection<? extends Thread> threads =
        ImmutableList.of(new IncThread(getTable(testConcurrentIncrement), A, inc1, rounds),
            new IncThread(getTable(testConcurrentIncrement), A, inc2, rounds),
            new IncAndGetThread(getTable(testConcurrentIncrement), A, R, 5, rounds),
            new IncAndGetThread(getTable(testConcurrentIncrement), A, R, 2, rounds));
    for (Thread t : threads) {
      t.start();
    }
    for (Thread t : threads) {
      t.join();
      if (t instanceof Closeable) {
        ((Closeable) t).close();
      }
    }
    assertEquals(rounds, Bytes.toLong(table.get(A, X)));
    assertEquals(3 * rounds, Bytes.toLong(table.get(A, Y)));
    assertEquals(2 * rounds, Bytes.toLong(table.get(A, Z)));
    assertEquals(7 * rounds, Bytes.toLong(table.get(A, R)));
  }

  @Override
  protected MetricsTable getTable(String name) throws Exception {
    DatasetId metricsDatasetInstanceId = NamespaceId.SYSTEM.dataset(name);
    DatasetProperties props = TableProperties.builder().setReadlessIncrementSupport(true).build();
    return DatasetsUtil.getOrCreateDataset(dsFramework, metricsDatasetInstanceId,
                                           MetricsTable.class.getName(), props, null);
  }
}
