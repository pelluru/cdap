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

package co.cask.cdap.data2.util.hbase;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data.hbase.HBaseTestBase;
import co.cask.cdap.data.hbase.HBaseTestFactory;
import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.spi.hbase.HBaseDDLExecutor;
import co.cask.cdap.test.SlowTests;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests reading and writing {@link CConfiguration} instances to an HBase table.
 */
@Category(SlowTests.class)
public class ConfigurationTableTest {

  @ClassRule
  public static final HBaseTestBase TEST_HBASE = new HBaseTestFactory().get();

  private static HBaseTableUtil tableUtil;
  private static CConfiguration cConf = CConfiguration.create();
  private static HBaseDDLExecutor ddlExecutor;

  @BeforeClass
  public static void setupBeforeClass() throws Exception {
    tableUtil = new HBaseTableUtilFactory(cConf).get();
    ddlExecutor = new HBaseDDLExecutorFactory(cConf, TEST_HBASE.getHBaseAdmin().getConfiguration()).get();
    ddlExecutor.createNamespaceIfNotExists(tableUtil.getHBaseNamespace(NamespaceId.SYSTEM));
  }

  @AfterClass
  public static void teardownAfterClass() throws Exception {
    tableUtil.deleteAllInNamespace(ddlExecutor, tableUtil.getHBaseNamespace(NamespaceId.SYSTEM),
                                   TEST_HBASE.getHBaseAdmin().getConfiguration());
    ddlExecutor.deleteNamespaceIfExists(tableUtil.getHBaseNamespace(NamespaceId.SYSTEM));
  }

  @Test
  public void testConfigurationSerialization() throws Exception {
    ConfigurationTable configTable = new ConfigurationTable(TEST_HBASE.getConfiguration());
    configTable.write(ConfigurationTable.Type.DEFAULT, cConf);

    String configTableQualifier = "configuration";
    TableId configTableId = tableUtil.createHTableId(NamespaceId.SYSTEM, configTableQualifier);
    String configTableName = tableUtil.buildHTableDescriptor(configTableId).build().getNameAsString();
    // the config table name minus the qualifier ('configuration'). Example: 'cdap.system.'
    String configTablePrefix = configTableName.substring(0, configTableName.length()  - configTableQualifier.length());

    CConfiguration cConf2 = configTable.read(ConfigurationTable.Type.DEFAULT, configTablePrefix);
    assertNotNull(cConf2);

    for (Map.Entry<String, String> e : cConf) {
      assertEquals("Configuration value mismatch (cConf -> cConf2) for key: " + e.getKey(),
                   e.getValue(), cConf2.get(e.getKey()));
    }
    for (Map.Entry<String, String> e : cConf2) {
      assertEquals("Configuration value mismatch (cConf2 -> cConf) for key: " + e.getKey(),
                   e.getValue(), cConf.get(e.getKey()));
    }
  }
}
