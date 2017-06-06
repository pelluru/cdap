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

package co.cask.cdap.explore.service;

import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.explore.client.ExploreExecutionResult;
import co.cask.cdap.explore.service.datasets.KeyExtendedStructValueTableDefinition;
import co.cask.cdap.explore.service.datasets.KeyStructValueTableDefinition;
import co.cask.cdap.explore.service.datasets.KeyValueTableDefinition;
import co.cask.cdap.explore.service.datasets.WritableKeyStructValueTableDefinition;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.DatasetModuleId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.test.XSlowTests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.tephra.Transaction;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 *
 */
@Category(XSlowTests.class)
public class WritableDatasetTestRun extends BaseHiveExploreServiceTest {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final DatasetModuleId keyExtendedStructValueTable =
    NAMESPACE_ID.datasetModule("keyExtendedStructValueTable");
  private static final DatasetModuleId kvTable = NAMESPACE_ID.datasetModule("kvTable");
  private static final DatasetModuleId writableKeyStructValueTable =
    NAMESPACE_ID.datasetModule("writableKeyStructValueTable");

  private static final DatasetId extendedTable = NAMESPACE_ID.dataset("extended_table");
  private static final DatasetId simpleTable = NAMESPACE_ID.dataset("simple_table");
  private static final DatasetModuleId otherKvTable = OTHER_NAMESPACE_ID.datasetModule("kvTable");
  private static final DatasetId otherSimpleTable = OTHER_NAMESPACE_ID.dataset("simple_table");
  private static final String simpleTableName = getDatasetHiveName(simpleTable);
  private static final String otherSimpleTableName = getDatasetHiveName(otherSimpleTable);
  private static final String extendedTableName = getDatasetHiveName(extendedTable);

  @BeforeClass
  public static void start() throws Exception {
    initialize(tmpFolder);

    datasetFramework.addModule(KEY_STRUCT_VALUE, new KeyStructValueTableDefinition.KeyStructValueTableModule());
    datasetFramework.addModule(OTHER_KEY_STRUCT_VALUE, new KeyStructValueTableDefinition.KeyStructValueTableModule());
  }

  private static void initKeyValueTable(DatasetId datasetInstanceId, boolean addData) throws Exception {
    // Performing admin operations to create dataset instance
    datasetFramework.addInstance("keyStructValueTable", datasetInstanceId, DatasetProperties.EMPTY);
    if (!addData) {
      return;
    }

    // Accessing dataset instance to perform data operations
    KeyStructValueTableDefinition.KeyStructValueTable table =
      datasetFramework.getDataset(datasetInstanceId, DatasetDefinition.NO_ARGUMENTS, null);
    Assert.assertNotNull(table);

    Transaction tx = transactionManager.startShort(100);
    table.startTx(tx);

    KeyStructValueTableDefinition.KeyValue.Value value1 =
      new KeyStructValueTableDefinition.KeyValue.Value("first", Lists.newArrayList(1, 2, 3, 4, 5));
    KeyStructValueTableDefinition.KeyValue.Value value2 =
      new KeyStructValueTableDefinition.KeyValue.Value("two", Lists.newArrayList(10, 11, 12, 13, 14));
    table.put("1", value1);
    table.put("2", value2);
    Assert.assertEquals(value1, table.get("1"));

    Assert.assertTrue(table.commitTx());

    transactionManager.canCommit(tx, table.getTxChanges());
    transactionManager.commit(tx);

    table.postTxCommit();
  }

  @AfterClass
  public static void stop() throws Exception {
    datasetFramework.deleteModule(KEY_STRUCT_VALUE);
    datasetFramework.deleteModule(OTHER_KEY_STRUCT_VALUE);
  }

  @Test
  public void writeIntoItselfTest() throws Exception {
    try {
      initKeyValueTable(MY_TABLE, true);
      ListenableFuture<ExploreExecutionResult> future =
        exploreClient.submit(NAMESPACE_ID, String.format("insert into table %s select * from %s",
                                                         MY_TABLE_NAME, MY_TABLE_NAME));
      ExploreExecutionResult result = future.get();
      result.close();

      // Assert the values have been inserted into the dataset
      KeyStructValueTableDefinition.KeyStructValueTable table =
        datasetFramework.getDataset(MY_TABLE, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);
      Transaction tx = transactionManager.startShort(100);
      table.startTx(tx);

      Assert.assertEquals(new KeyStructValueTableDefinition.KeyValue.Value("first", Lists.newArrayList(1, 2, 3, 4, 5)),
                          table.get("1_2"));
      Assert.assertEquals(new KeyStructValueTableDefinition.KeyValue.Value("two",
                                                                           Lists.newArrayList(10, 11, 12, 13, 14)),
                          table.get("2_2"));

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx, table.getTxChanges());
      transactionManager.commit(tx);
      table.postTxCommit();

      // Make sure Hive also sees those values
      result = exploreClient.submit(NAMESPACE_ID, "select * from " + MY_TABLE_NAME).get();
      Assert.assertEquals("1", result.next().getColumns().get(0).toString());
      Assert.assertEquals("1_2", result.next().getColumns().get(0).toString());
      Assert.assertEquals("2", result.next().getColumns().get(0).toString());
      Assert.assertEquals("2_2", result.next().getColumns().get(0).toString());
      Assert.assertFalse(result.hasNext());
      result.close();
    } finally {
      datasetFramework.deleteInstance(MY_TABLE);
    }
  }

  @Test
  public void testTablesWithSpecialChars() throws Exception {
    // '.' are replaced with "_" in hive, so create a dataset with . in name.
    DatasetId myTable1 = NAMESPACE_ID.dataset("dot.table");
    // '_' are replaced with "_" in hive, so create a dataset with . in name.
    DatasetId myTable2 = NAMESPACE_ID.dataset("hyphen-table");
    try {
      initKeyValueTable(myTable1, true);
      initKeyValueTable(myTable2, true);

      ExploreExecutionResult result = exploreClient.submit(NAMESPACE_ID,
                                                           "select * from dataset_dot_table").get();

      Assert.assertEquals("1", result.next().getColumns().get(0).toString());
      result.close();

      result = exploreClient.submit(NAMESPACE_ID, "select * from dataset_hyphen_table").get();
      Assert.assertEquals("1", result.next().getColumns().get(0).toString());
      result.close();

    } finally {
      datasetFramework.deleteInstance(myTable1);
      datasetFramework.deleteInstance(myTable2);
    }
  }

  @Test
  public void writeIntoOtherDatasetTest() throws Exception {

    datasetFramework.addModule(keyExtendedStructValueTable,
                               new KeyExtendedStructValueTableDefinition.KeyExtendedStructValueTableModule());
    datasetFramework.addInstance("keyExtendedStructValueTable", extendedTable, DatasetProperties.EMPTY);
    try {
      initKeyValueTable(MY_TABLE, true);
      // Accessing dataset instance to perform data operations
      KeyExtendedStructValueTableDefinition.KeyExtendedStructValueTable table =
        datasetFramework.getDataset(extendedTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);

      Transaction tx1 = transactionManager.startShort(100);
      table.startTx(tx1);

      KeyExtendedStructValueTableDefinition.KeyExtendedValue value1 =
        new KeyExtendedStructValueTableDefinition.KeyExtendedValue(
          "10",
          new KeyStructValueTableDefinition.KeyValue.Value("ten", Lists.newArrayList(10, 11, 12)),
          20);
      table.put("10", value1);
      Assert.assertEquals(value1, table.get("10"));

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx1, table.getTxChanges());
      transactionManager.commit(tx1);
      table.postTxCommit();

      String query = String.format("insert into table %s select key,value from %s",
                                   MY_TABLE_NAME, extendedTableName);
      ListenableFuture<ExploreExecutionResult> future = exploreClient.submit(NAMESPACE_ID, query);
      ExploreExecutionResult result = future.get();
      result.close();

      result = exploreClient.submit(NAMESPACE_ID, "select * from " + MY_TABLE_NAME).get();
      Assert.assertEquals("1", result.next().getColumns().get(0).toString());
      Assert.assertEquals("10_2", result.next().getColumns().get(0).toString());
      Assert.assertEquals("2", result.next().getColumns().get(0).toString());
      Assert.assertFalse(result.hasNext());
      result.close();

      // Test insert overwrite
      query = String.format("insert overwrite table %s select key,value from %s", MY_TABLE_NAME, extendedTableName);
      result = exploreClient.submit(NAMESPACE_ID,
                                    query).get();
      result.close();
      result = exploreClient.submit(NAMESPACE_ID, "select * from " + MY_TABLE_NAME).get();
      result.hasNext();

    } finally {
      datasetFramework.deleteInstance(MY_TABLE);
      datasetFramework.deleteInstance(extendedTable);
      datasetFramework.deleteModule(keyExtendedStructValueTable);
    }
  }

  @Test
  public void writeIntoNonScannableDataset() throws Exception {
    DatasetId writableTable = NAMESPACE_ID.dataset("writable_table");
    String writableTableName = getDatasetHiveName(writableTable);
    datasetFramework.addModule(keyExtendedStructValueTable,
                               new KeyExtendedStructValueTableDefinition.KeyExtendedStructValueTableModule());
    datasetFramework.addInstance("keyExtendedStructValueTable", extendedTable, DatasetProperties.EMPTY);

    datasetFramework.addModule(writableKeyStructValueTable,
                               new WritableKeyStructValueTableDefinition.KeyStructValueTableModule());
    datasetFramework.addInstance("writableKeyStructValueTable", writableTable, DatasetProperties.EMPTY);
    try {
      // Accessing dataset instance to perform data operations
      KeyExtendedStructValueTableDefinition.KeyExtendedStructValueTable table =
        datasetFramework.getDataset(extendedTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);

      Transaction tx1 = transactionManager.startShort(100);
      table.startTx(tx1);

      KeyExtendedStructValueTableDefinition.KeyExtendedValue value1 =
        new KeyExtendedStructValueTableDefinition.KeyExtendedValue(
          "10",
          new KeyStructValueTableDefinition.KeyValue.Value("ten", Lists.newArrayList(10, 11, 12)),
          20);
      table.put("10", value1);
      Assert.assertEquals(value1, table.get("10"));

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx1, table.getTxChanges());
      transactionManager.commit(tx1);
      table.postTxCommit();

      String query = "insert into table " + writableTableName + " select key,value from " + extendedTableName;
      ListenableFuture<ExploreExecutionResult> future = exploreClient.submit(NAMESPACE_ID, query);
      ExploreExecutionResult result = future.get();
      result.close();

      KeyStructValueTableDefinition.KeyStructValueTable table2 =
        datasetFramework.getDataset(writableTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);
      Transaction tx = transactionManager.startShort(100);
      Assert.assertNotNull(table2);
      table2.startTx(tx);

      Assert.assertEquals(new KeyStructValueTableDefinition.KeyValue.Value("ten", Lists.newArrayList(10, 11, 12)),
                          table2.get("10_2"));

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx, table.getTxChanges());
      transactionManager.commit(tx);
      table.postTxCommit();

    } finally {
      datasetFramework.deleteInstance(writableTable);
      datasetFramework.deleteInstance(extendedTable);
      datasetFramework.deleteModule(writableKeyStructValueTable);
      datasetFramework.deleteModule(keyExtendedStructValueTable);
    }
  }

  @Test
  public void multipleInsertsTest() throws Exception {
    DatasetId myTable1 = NAMESPACE_ID.dataset("my_table_1");
    DatasetId myTable2 = NAMESPACE_ID.dataset("my_table_2");
    DatasetId myTable3 = NAMESPACE_ID.dataset("my_table_3");
    String myTable1HiveName = getDatasetHiveName(myTable1);
    String myTable2HiveName = getDatasetHiveName(myTable2);
    String myTable3HiveName = getDatasetHiveName(myTable3);
    try {
      initKeyValueTable(MY_TABLE, true);
      initKeyValueTable(myTable1, false);
      initKeyValueTable(myTable2, false);
      initKeyValueTable(myTable3, false);
      ListenableFuture<ExploreExecutionResult> future =
        exploreClient.submit(NAMESPACE_ID, String.format("from %s insert into table %s select * where key='1' " +
                                                           "insert into table %s select * where key='2' " +
                                                           "insert into table %s select *",
                                                         MY_TABLE_NAME, myTable1HiveName,
                                                         myTable2HiveName, myTable3HiveName));
      ExploreExecutionResult result = future.get();
      result.close();

      result = exploreClient.submit(NAMESPACE_ID, "select * from " + myTable2HiveName).get();
      Assert.assertEquals("2_2", result.next().getColumns().get(0).toString());
      Assert.assertFalse(result.hasNext());
      result.close();

      result = exploreClient.submit(NAMESPACE_ID, "select * from " + myTable1HiveName).get();
      Assert.assertEquals("1_2", result.next().getColumns().get(0).toString());
      Assert.assertFalse(result.hasNext());
      result.close();

      result = exploreClient.submit(NAMESPACE_ID, "select * from " + myTable3HiveName).get();
      Assert.assertEquals("1_2", result.next().getColumns().get(0).toString());
      Assert.assertEquals("2_2", result.next().getColumns().get(0).toString());
      Assert.assertFalse(result.hasNext());
      result.close();
    } finally {
      datasetFramework.deleteInstance(MY_TABLE);
      datasetFramework.deleteInstance(myTable1);
      datasetFramework.deleteInstance(myTable2);
      datasetFramework.deleteInstance(myTable3);
    }
  }

  @Test
  public void writeFromNativeTableIntoDatasetTest() throws Exception {

    datasetFramework.addModule(kvTable, new KeyValueTableDefinition.KeyValueTableModule());
    datasetFramework.addInstance("kvTable", simpleTable, DatasetProperties.EMPTY);
    try {
      URL loadFileUrl = getClass().getResource("/test_table.dat");
      Assert.assertNotNull(loadFileUrl);

      exploreClient.submit(NAMESPACE_ID,
                           "create table test (first INT, second STRING) ROW FORMAT " +
                             "DELIMITED FIELDS TERMINATED BY '\\t'").get().close();
      exploreClient.submit(NAMESPACE_ID,
                           "LOAD DATA LOCAL INPATH '" + new File(loadFileUrl.toURI()).getAbsolutePath() +
                             "' INTO TABLE test").get().close();

      exploreClient.submit(NAMESPACE_ID,
                           "insert into table " + simpleTableName + " select * from test").get().close();

      assertSelectAll(NAMESPACE_ID, simpleTableName, ImmutableList.<List<Object>>of(
        ImmutableList.<Object>of(1, "one"),
        ImmutableList.<Object>of(2, "two"),
        ImmutableList.<Object>of(3, "three"),
        ImmutableList.<Object>of(4, "four"),
        ImmutableList.<Object>of(5, "five")
      ));

    } finally {
      exploreClient.submit(NAMESPACE_ID, "drop table if exists test").get().close();
      datasetFramework.deleteInstance(simpleTable);
      datasetFramework.deleteModule(kvTable);
    }
  }

  @Test
  public void writeFromDatasetIntoNativeTableTest() throws Exception {

    datasetFramework.addModule(kvTable, new KeyValueTableDefinition.KeyValueTableModule());
    datasetFramework.addInstance("kvTable", simpleTable, DatasetProperties.EMPTY);
    try {
      exploreClient.submit(NAMESPACE_ID, "create table test (first INT, second STRING) ROW FORMAT " +
                             "DELIMITED FIELDS TERMINATED BY '\\t'").get().close();

      // Accessing dataset instance to perform data operations
      KeyValueTableDefinition.KeyValueTable table =
        datasetFramework.getDataset(simpleTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);

      Transaction tx1 = transactionManager.startShort(100);
      table.startTx(tx1);

      table.put(10, "ten");
      Assert.assertEquals("ten", table.get(10));

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx1, table.getTxChanges());
      transactionManager.commit(tx1);
      table.postTxCommit();

      exploreClient.submit(NAMESPACE_ID,
                           "insert into table test select * from " + simpleTableName).get().close();

      assertSelectAll(NAMESPACE_ID, "test", ImmutableList.<List<Object>>of(
        ImmutableList.<Object>of(10, "ten")
      ));

    } finally {
      exploreClient.submit(NAMESPACE_ID, "drop table if exists test").get().close();
      datasetFramework.deleteInstance(simpleTable);
      datasetFramework.deleteModule(kvTable);
    }
  }

  @Test
  public void writeFromAnotherNamespace() throws Exception {
    datasetFramework.addModule(kvTable, new KeyValueTableDefinition.KeyValueTableModule());
    datasetFramework.addInstance("kvTable", simpleTable, DatasetProperties.EMPTY);

    datasetFramework.addModule(otherKvTable, new KeyValueTableDefinition.KeyValueTableModule());
    datasetFramework.addInstance("kvTable", otherSimpleTable, DatasetProperties.EMPTY);

    try {

      ExploreExecutionResult result = exploreClient.submit(OTHER_NAMESPACE_ID,
                                                           "select * from " + simpleTableName).get();
      Assert.assertFalse(result.hasNext());

      // Accessing dataset instance to perform data operations
      KeyValueTableDefinition.KeyValueTable table =
        datasetFramework.getDataset(simpleTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);

      Transaction tx = transactionManager.startShort(100);
      table.startTx(tx);

      table.put(1, "one");

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx, table.getTxChanges());
      transactionManager.commit(tx);
      table.postTxCommit();

      String query = String.format("insert into table %s select * from cdap_namespace.%s",
                                   otherSimpleTableName, simpleTableName);
      exploreClient.submit(OTHER_NAMESPACE_ID, query).get().close();

      assertSelectAll(NAMESPACE_ID, simpleTableName, ImmutableList.<List<Object>>of(
        ImmutableList.<Object>of(1, "one")
      ));

      // Write into otherSimpleTable and assert that it doesn't show up in queries over simpleTable
      table = datasetFramework.getDataset(otherSimpleTable, DatasetDefinition.NO_ARGUMENTS, null);
      Assert.assertNotNull(table);

      tx = transactionManager.startShort(100);
      table.startTx(tx);

      table.put(2, "two");

      Assert.assertTrue(table.commitTx());
      transactionManager.canCommit(tx, table.getTxChanges());
      transactionManager.commit(tx);
      table.postTxCommit();

      assertSelectAll(OTHER_NAMESPACE_ID, otherSimpleTableName, ImmutableList.<List<Object>>of(
        ImmutableList.<Object>of(1, "one"),
        ImmutableList.<Object>of(2, "two")
      ));

      assertSelectAll(NAMESPACE_ID, simpleTableName, ImmutableList.<List<Object>>of(
        ImmutableList.<Object>of(1, "one")
      ));

    } finally {
      datasetFramework.deleteInstance(simpleTable);
      datasetFramework.deleteInstance(otherSimpleTable);
      datasetFramework.deleteModule(kvTable);
      datasetFramework.deleteModule(otherKvTable);
    }
  }

  private void assertSelectAll(NamespaceId namespace, String table,
                               List<List<Object>> expectedResults) throws Exception {
    ExploreExecutionResult result = exploreClient.submit(namespace, "select * from " + table).get();
    for (List<Object> expectedResult : expectedResults) {
      Assert.assertEquals(expectedResult, result.next().getColumns());
    }
    Assert.assertFalse(result.hasNext());
    result.close();
  }

  // TODO test insert overwrite table: overwrite is the same as into
  // TODO test trying to write with incompatible types
}
