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

import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.NonCustomLocationUnitTestModule;
import co.cask.cdap.data.runtime.DataFabricLevelDBModule;
import co.cask.cdap.data.runtime.DataSetsModules;
import co.cask.cdap.data.runtime.TransactionMetricsModule;
import co.cask.cdap.data2.dataset2.lib.table.BufferingTableTest;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import co.cask.cdap.security.authorization.AuthorizationTestModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Map;

/**
 * test for LevelDB tables.
 */
public class LevelDBTableTest extends BufferingTableTest<LevelDBTable> {

  private static final LevelDBTableDefinition TABLE_DEFINITION = new LevelDBTableDefinition("foo");

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  static LevelDBTableService service;
  static Injector injector = null;

  private static CConfiguration cConf;

  @BeforeClass
  public static void init() throws Exception {
    cConf = CConfiguration.create();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, tmpFolder.newFolder().getAbsolutePath());
    injector = Guice.createInjector(
      new ConfigModule(cConf),
      new NonCustomLocationUnitTestModule().getModule(),
      new DiscoveryRuntimeModule().getStandaloneModules(),
      new DataSetsModules().getStandaloneModules(),
      new DataFabricLevelDBModule(),
      new TransactionMetricsModule(),
      new AuthorizationTestModule(),
      new AuthorizationEnforcementModule().getStandaloneModules(),
      new AuthenticationContextModules().getMasterModule());
    service = injector.getInstance(LevelDBTableService.class);
  }

  @Override
  protected LevelDBTable getTable(DatasetContext datasetContext, String name,
                                  DatasetProperties props, Map<String, String> runtimeArguments) throws Exception {
    DatasetSpecification spec = DatasetSpecification.builder(name, "table").properties(props.getProperties()).build();
    return new LevelDBTable(datasetContext, name, service, cConf, spec);
  }

  @Override
  protected LevelDBTableAdmin getTableAdmin(DatasetContext datasetContext, String name,
                                            DatasetProperties props) throws IOException {
    DatasetSpecification spec = TABLE_DEFINITION.configure(name, props);
    return new LevelDBTableAdmin(datasetContext, spec, service, cConf);
  }

  @Override
  protected boolean isReadlessIncrementSupported() {
    return false;
  }

  @Test
  public void testTablesSurviveAcrossRestart() throws Exception {
    // todo make this test run for hbase, too - requires refactoring of their injection
    // test on ASCII table name but also on some non-ASCII ones
    final String[] tableNames = { "table", "t able", "t\u00C3ble", "100%" };

    // create a table and verify it is in the list of tables
    for (String tableName : tableNames) {
      LevelDBTableAdmin admin = getTableAdmin(CONTEXT1, tableName, DatasetProperties.EMPTY);
      admin.create();
      Assert.assertTrue(admin.exists());
    }

    // clearing in-mem cache - mimicing JVM restart
    service.clearTables();
    for (String tableName : tableNames) {
      service.list().contains(tableName);
    }
  }
}
