/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package co.cask.cdap.explore.service;

import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.ExploreProperties;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.api.dataset.table.TableProperties;
import co.cask.cdap.explore.client.ExploreExecutionResult;
import co.cask.cdap.explore.service.datasets.EmailTableDefinition;
import co.cask.cdap.explore.service.datasets.TableWrapperDefinition;
import co.cask.cdap.proto.ColumnDesc;
import co.cask.cdap.proto.QueryResult;
import co.cask.cdap.proto.QueryStatus;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.DatasetModuleId;
import co.cask.cdap.test.SlowTests;
import com.google.common.collect.Lists;
import org.apache.tephra.Transaction;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Tests exploration of record scannables that are scannables of StructuredRecord.
 */
@Category(SlowTests.class)
public class HiveExploreStructuredRecordTestRun extends BaseHiveExploreServiceTest {
  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  @BeforeClass
  public static void start() throws Exception {
    initialize(tmpFolder);

    DatasetModuleId moduleId = NAMESPACE_ID.datasetModule("email");
    datasetFramework.addModule(moduleId, new EmailTableDefinition.EmailTableModule());
    datasetFramework.addInstance("email", MY_TABLE, DatasetProperties.EMPTY);

    // Accessing dataset instance to perform data operations
    EmailTableDefinition.EmailTable table = datasetFramework.getDataset(MY_TABLE, DatasetDefinition.NO_ARGUMENTS, null);
    Assert.assertNotNull(table);

    Transaction tx1 = transactionManager.startShort(100);
    table.startTx(tx1);

    table.writeEmail("email1", "this is the subject", "this is the body", "sljackson@boss.com");

    Assert.assertTrue(table.commitTx());

    transactionManager.canCommit(tx1, table.getTxChanges());
    transactionManager.commit(tx1);

    table.postTxCommit();


    datasetFramework.addModule(NAMESPACE_ID.datasetModule("TableWrapper"),
                               new TableWrapperDefinition.Module());
  }

  @AfterClass
  public static void stop() throws Exception {
    datasetFramework.deleteInstance(MY_TABLE);
    datasetFramework.deleteModule(NAMESPACE_ID.datasetModule("TableWrapper"));
  }

  @Test
  public void testCreateDropCustomDBAndTable() throws Exception {
    testCreateDropCustomDBAndTable("databasex", null);
    testCreateDropCustomDBAndTable(null, "tablex");
    testCreateDropCustomDBAndTable("databasey", "tabley");
  }

  private void testCreateDropCustomDBAndTable(@Nullable String database, @Nullable String tableName) throws Exception {
    String datasetName = "cdccat";
    DatasetId datasetId = NAMESPACE_ID.dataset(datasetName);
    ExploreProperties.Builder props = ExploreProperties.builder();
    if (tableName != null) {
      props.setExploreTableName(tableName);
    } else {
      tableName = getDatasetHiveName(datasetId);
    }
    if (database != null) {
      runCommand(NAMESPACE_ID, "create database if not exists " + database, false, null, null);
      props.setExploreDatabaseName(database);
    }
    try {
      datasetFramework.addInstance("email", datasetId, props.build());
      if (database == null) {
        runCommand(NAMESPACE_ID, "show tables", true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(MY_TABLE_NAME)),
                                      new QueryResult(Lists.<Object>newArrayList(tableName))));
      } else {
        runCommand(NAMESPACE_ID, "show tables in " + database, true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(tableName))));
      }
      datasetFramework.deleteInstance(datasetId);
      if (database == null) {
        runCommand(NAMESPACE_ID, "show tables", true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(MY_TABLE_NAME))));
      } else {
        runCommand(NAMESPACE_ID, "show tables in " + database, false, null,
                   Collections.<QueryResult>emptyList());
      }
    } finally {
      if (database != null) {
        runCommand(NAMESPACE_ID, "drop database if exists " + database + "cascade", false, null, null);
      }
    }
  }


  @Test(expected = IllegalArgumentException.class)
  public void testMissingSchemaFails() throws Exception {
    DatasetId instanceId = NAMESPACE_ID.dataset("badtable");
    datasetFramework.addInstance("TableWrapper", instanceId, DatasetProperties.EMPTY);

    DatasetSpecification spec = datasetFramework.getDatasetSpec(instanceId);
    try {
      exploreTableManager.enableDataset(instanceId, spec, false);
    } finally {
      datasetFramework.deleteInstance(instanceId);
    }
  }

  @Test
  public void testRecordScannableAndWritableIsOK() throws Exception {
    DatasetId instanceId = NAMESPACE_ID.dataset("tabul");
    datasetFramework.addInstance("TableWrapper", instanceId, DatasetProperties.builder()
      .add(DatasetProperties.SCHEMA,
           Schema.recordOf("intRecord", Schema.Field.of("x", Schema.of(Schema.Type.STRING))).toString())
      .build());

    DatasetSpecification spec = datasetFramework.getDatasetSpec(instanceId);
    try {
      exploreTableManager.enableDataset(instanceId, spec, false);
      runCommand(NAMESPACE_ID, "describe dataset_tabul",
        true,
        Lists.newArrayList(
          new ColumnDesc("col_name", "STRING", 1, "from deserializer"),
          new ColumnDesc("data_type", "STRING", 2, "from deserializer"),
          new ColumnDesc("comment", "STRING", 3, "from deserializer")
        ),
        Lists.newArrayList(
          new QueryResult(Lists.<Object>newArrayList("x", "string", "from deserializer"))
        )
      );
    } finally {
      datasetFramework.deleteInstance(instanceId);
    }
  }

  @Test
  public void testSchema() throws Exception {
    runCommand(NAMESPACE_ID, "describe " + MY_TABLE_NAME,
               true,
               Lists.newArrayList(
                 new ColumnDesc("col_name", "STRING", 1, "from deserializer"),
                 new ColumnDesc("data_type", "STRING", 2, "from deserializer"),
                 new ColumnDesc("comment", "STRING", 3, "from deserializer")
               ),
               Lists.newArrayList(
                 new QueryResult(Lists.<Object>newArrayList("id", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("subject", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("body", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("sender", "string", "from deserializer"))
               )
    );
  }

  @Test
  public void testSelectStar() throws Exception {
    List<ColumnDesc> expectedSchema = Lists.newArrayList(
      new ColumnDesc(MY_TABLE_NAME + ".id", "STRING", 1, null),
      new ColumnDesc(MY_TABLE_NAME + ".subject", "STRING", 2, null),
      new ColumnDesc(MY_TABLE_NAME + ".body", "STRING", 3, null),
      new ColumnDesc(MY_TABLE_NAME + ".sender", "STRING", 4, null)
    );
    ExploreExecutionResult results = exploreClient.submit(NAMESPACE_ID, "select * from " + MY_TABLE_NAME).get();
    // check schema
    Assert.assertEquals(expectedSchema, results.getResultSchema());
    List<Object> columns = results.next().getColumns();
    // check results
    Assert.assertEquals("email1", columns.get(0));
    Assert.assertEquals("this is the subject", columns.get(1));
    Assert.assertEquals("this is the body", columns.get(2));
    Assert.assertEquals("sljackson@boss.com", columns.get(3));
    // should not be any more
    Assert.assertFalse(results.hasNext());
  }

  @Test
  public void testSelect() throws Exception {
    String command = String.format("select sender from %s where body='this is the body'", MY_TABLE_NAME);
    runCommand(NAMESPACE_ID, command,
               true,
               Lists.newArrayList(new ColumnDesc("sender", "STRING", 1, null)),
               Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList("sljackson@boss.com")))
    );
  }

  @Test
  public void testInsert() throws Exception {
    DatasetId copyTable = NAMESPACE_ID.dataset("emailCopy");
    datasetFramework.addInstance(Table.class.getName(), copyTable, TableProperties.builder()
      .setSchema(EmailTableDefinition.SCHEMA)
      .setRowFieldName("id")
      .build());
    try {
      String command = String.format("insert into %s select * from %s",
        getDatasetHiveName(copyTable), MY_TABLE_NAME);
      ExploreExecutionResult result = exploreClient.submit(NAMESPACE_ID, command).get();
      Assert.assertEquals(QueryStatus.OpStatus.FINISHED, result.getStatus().getStatus());

      command = String.format("select id, subject, body, sender from %s", getDatasetHiveName(copyTable));
      runCommand(NAMESPACE_ID, command,
        true,
        Lists.newArrayList(new ColumnDesc("id", "STRING", 1, null),
                           new ColumnDesc("subject", "STRING", 2, null),
                           new ColumnDesc("body", "STRING", 3, null),
                           new ColumnDesc("sender", "STRING", 4, null)),
        Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(
          "email1", "this is the subject", "this is the body", "sljackson@boss.com")))
      );
    } finally {
      datasetFramework.deleteInstance(copyTable);
    }
  }
}
