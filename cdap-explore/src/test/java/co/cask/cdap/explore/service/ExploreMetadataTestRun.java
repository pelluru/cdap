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

package co.cask.cdap.explore.service;

import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.explore.client.ExploreExecutionResult;
import co.cask.cdap.explore.service.datasets.KeyStructValueTableDefinition;
import co.cask.cdap.proto.ColumnDesc;
import co.cask.cdap.proto.QueryResult;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.test.SlowTests;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests explore metadata endpoints.
 */
@Category(SlowTests.class)
public class ExploreMetadataTestRun extends BaseHiveExploreServiceTest {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final DatasetId otherTable = NAMESPACE_ID.dataset("other_table");
  private static final String otherTableName = getDatasetHiveName(otherTable);
  private static final DatasetId namespacedOtherTable = OTHER_NAMESPACE_ID.dataset("other_table");
  private static final String namespacedOtherTableName = getDatasetHiveName(namespacedOtherTable);

  @BeforeClass
  public static void start() throws Exception {
    initialize(tmpFolder);

    datasetFramework.addModule(KEY_STRUCT_VALUE, new KeyStructValueTableDefinition.KeyStructValueTableModule());
    datasetFramework.addModule(OTHER_KEY_STRUCT_VALUE, new KeyStructValueTableDefinition.KeyStructValueTableModule());

    // Performing admin operations to create dataset instance
    datasetFramework.addInstance("keyStructValueTable", MY_TABLE, DatasetProperties.EMPTY);
    datasetFramework.addInstance("keyStructValueTable", otherTable, DatasetProperties.EMPTY);
    datasetFramework.addInstance("keyStructValueTable", OTHER_MY_TABLE, DatasetProperties.EMPTY);
    datasetFramework.addInstance("keyStructValueTable", namespacedOtherTable, DatasetProperties.EMPTY);
  }

  @AfterClass
  public static void stop() throws Exception {
    datasetFramework.deleteInstance(MY_TABLE);
    datasetFramework.deleteInstance(otherTable);
    datasetFramework.deleteInstance(OTHER_MY_TABLE);
    datasetFramework.deleteInstance(namespacedOtherTable);
    datasetFramework.deleteModule(KEY_STRUCT_VALUE);
    datasetFramework.deleteModule(OTHER_KEY_STRUCT_VALUE);
  }

  @Test
  public void testGetTables() throws Exception {
    ListenableFuture<ExploreExecutionResult> future;

    // All tables
    future = getExploreClient().tables(null, null, "%", null);
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_CAT", "STRING", 1, "Catalog name. NULL if not applicable."),
                            new ColumnDesc("TABLE_SCHEM", "STRING", 2, "Schema name."),
                            new ColumnDesc("TABLE_NAME", "STRING", 3, "Table name."),
                            new ColumnDesc("TABLE_TYPE", "STRING", 4,
                                           "The table type, e.g. \"TABLE\", \"VIEW\", etc."),
                            new ColumnDesc("REMARKS", "STRING", 5, "Comments about the table.")
                          ),
                          Lists.newArrayList(
                            new QueryResult(Lists.<Object>newArrayList(
                              "", NAMESPACE_DATABASE, MY_TABLE_NAME, "TABLE", "CDAP Dataset")),
                            new QueryResult(Lists.<Object>newArrayList(
                              "", NAMESPACE_DATABASE, otherTableName, "TABLE", "CDAP Dataset")),
                            new QueryResult(Lists.<Object>newArrayList(
                              "", OTHER_NAMESPACE_DATABASE, OTHER_MY_TABLE_NAME, "TABLE", "CDAP Dataset")),
                            new QueryResult(Lists.<Object>newArrayList(
                              "", OTHER_NAMESPACE_DATABASE, namespacedOtherTableName, "TABLE", "CDAP Dataset")))
    );

    // Pattern on table name
    future = getExploreClient().tables(null, null, "dataset_other%", null);
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_CAT", "STRING", 1, "Catalog name. NULL if not applicable."),
                            new ColumnDesc("TABLE_SCHEM", "STRING", 2, "Schema name."),
                            new ColumnDesc("TABLE_NAME", "STRING", 3, "Table name."),
                            new ColumnDesc("TABLE_TYPE", "STRING", 4, "The table type, e.g. \"TABLE\", \"VIEW\", etc."),
                            new ColumnDesc("REMARKS", "STRING", 5, "Comments about the table.")
                          ),
                          Lists.newArrayList(
                            new QueryResult(Lists.<Object>newArrayList(
                              "", NAMESPACE_DATABASE, otherTableName, "TABLE", "CDAP Dataset")),
                            new QueryResult(Lists.<Object>newArrayList(
                             "", OTHER_NAMESPACE_DATABASE, namespacedOtherTableName, "TABLE", "CDAP Dataset")))

    );

    // Pattern on database
    future = getExploreClient().tables(null, OTHER_NAMESPACE_ID.getNamespace(), "%", null);
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_CAT", "STRING", 1, "Catalog name. NULL if not applicable."),
                            new ColumnDesc("TABLE_SCHEM", "STRING", 2, "Schema name."),
                            new ColumnDesc("TABLE_NAME", "STRING", 3, "Table name."),
                            new ColumnDesc("TABLE_TYPE", "STRING", 4,
                                           "The table type, e.g. \"TABLE\", \"VIEW\", etc."),
                            new ColumnDesc("REMARKS", "STRING", 5, "Comments about the table.")
                          ),
                          Lists.newArrayList(
                            new QueryResult(Lists.<Object>newArrayList(
                              "", OTHER_NAMESPACE_DATABASE, OTHER_MY_TABLE_NAME, "TABLE", "CDAP Dataset")),
                            new QueryResult(Lists.<Object>newArrayList(
                              "", OTHER_NAMESPACE_DATABASE, namespacedOtherTableName, "TABLE", "CDAP Dataset")))
    );
  }

  @Test
  public void testGetCatalogs() throws Exception {
    ListenableFuture<ExploreExecutionResult> future;
    future = getExploreClient().catalogs();
    assertStatementResult(future, false,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_CAT", "STRING", 1, "Catalog name. NULL if not applicable.")
                          ),
                          Lists.<QueryResult>newArrayList());
  }

  @Test
  public void testGetSchemas() throws Exception {
    ListenableFuture<ExploreExecutionResult> future;

    future = getExploreClient().schemas(null, null);
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_SCHEM", "STRING", 1, "Schema name."),
                            new ColumnDesc("TABLE_CATALOG", "STRING", 2, "Catalog name.")
                          ),
                          Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(NAMESPACE_DATABASE, "")),
                                             new QueryResult(Lists.<Object>newArrayList(OTHER_NAMESPACE_DATABASE, "")),
                                             new QueryResult(Lists.<Object>newArrayList(DEFAULT_DATABASE, "")))
    );

    future = getExploreClient().schemas(null, NAMESPACE_ID.getNamespace());
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_SCHEM", "STRING", 1, "Schema name."),
                            new ColumnDesc("TABLE_CATALOG", "STRING", 2, "Catalog name.")
                          ),
                          Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(NAMESPACE_DATABASE, "")))
    );

    future = getExploreClient().schemas(null, OTHER_NAMESPACE_ID.getNamespace());
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_SCHEM", "STRING", 1, "Schema name."),
                            new ColumnDesc("TABLE_CATALOG", "STRING", 2, "Catalog name.")
                          ),
                          Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(OTHER_NAMESPACE_DATABASE, "")))
    );
  }

  @Test
  public void testGetTypeInfo() throws Exception {
    ListenableFuture<ExploreExecutionResult> future;

    future = getExploreClient().dataTypes();
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TYPE_NAME", "STRING", 1, "Type name"),
                            new ColumnDesc("DATA_TYPE", "INT", 2, "SQL data type from java.sql.Types"),
                            new ColumnDesc("PRECISION", "INT", 3, "Maximum precision"),
                            new ColumnDesc("LITERAL_PREFIX", "STRING", 4,
                                           "Prefix used to quote a literal (may be null)"),
                            new ColumnDesc("LITERAL_SUFFIX", "STRING", 5,
                                           "Suffix used to quote a literal (may be null)"),
                            new ColumnDesc("CREATE_PARAMS", "STRING", 6,
                                           "Parameters used in creating the type (may be null)"),
                            new ColumnDesc("NULLABLE", "SMALLINT", 7, "Can you use NULL for this type"),
                            new ColumnDesc("CASE_SENSITIVE", "BOOLEAN", 8, "Is it case sensitive"),
                            new ColumnDesc("SEARCHABLE", "SMALLINT", 9, "Can you use \"WHERE\" based on this type"),
                            new ColumnDesc("UNSIGNED_ATTRIBUTE", "BOOLEAN", 10, "Is it unsigned"),
                            new ColumnDesc("FIXED_PREC_SCALE", "BOOLEAN", 11, "Can it be a money value"),
                            new ColumnDesc("AUTO_INCREMENT", "BOOLEAN", 12,
                                           "Can it be used for an auto-increment value"),
                            new ColumnDesc("LOCAL_TYPE_NAME", "STRING", 13,
                                           "Localized version of type name (may be null)"),
                            new ColumnDesc("MINIMUM_SCALE", "SMALLINT", 14, "Minimum scale supported"),
                            new ColumnDesc("MAXIMUM_SCALE", "SMALLINT", 15, "Maximum scale supported"),
                            new ColumnDesc("SQL_DATA_TYPE", "INT", 16, "Unused"),
                            new ColumnDesc("SQL_DATETIME_SUB", "INT", 17, "Unused"),
                            new ColumnDesc("NUM_PREC_RADIX", "INT", 18, "Usually 2 or 10")
                          ),
                          Lists.newArrayList(
                            new QueryResult(Lists.<Object>newArrayList(
                              "VOID", 0, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "BOOLEAN", 16, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0, null,
                              null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "TINYINT", -6, 3, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "SMALLINT", 5, 5, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "INT", 4, 10, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "BIGINT", -5, 19, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "FLOAT", 6, 7, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "DOUBLE", 8, 15, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "STRING", 12, null, null, null, null, (short) 1, true,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "CHAR", 1, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "VARCHAR", 12, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "DATE", 91, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "TIMESTAMP", 93, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "INTERVAL_YEAR_MONTH", 1111, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "INTERVAL_DAY_TIME", 1111, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "BINARY", -2, null, null, null, null, (short) 1, false,
                              (short) 3, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "DECIMAL", 3, 38, null, null, null, (short) 1, false,
                              (short) 3, false, false, false, null, (short) 0, (short) 0,
                              null, null, 10)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "ARRAY", 2003, null, null, null, null, (short) 1, false,
                              (short) 0, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "MAP", 2000, null, null, null, null, (short) 1, false,
                              (short) 0, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "STRUCT", 2002, null, null, null, null, (short) 1, false,
                              (short) 0, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "UNIONTYPE", 1111, null, null, null, null, (short) 1,
                              false, (short) 0, true, false, false, null, (short) 0, (short) 0,
                              null, null, null)),
                            new QueryResult(Lists.<Object>newArrayList(
                              "USER_DEFINED", 1111, null, null, null, null, (short) 1,
                              false, (short) 0, true, false, false, null, (short) 0, (short) 0,
                              null, null, null))
                          )
    );
  }

  @Test
  public void testGetColumns() throws Exception {
    ArrayList<ColumnDesc> expectedColumnDescs = Lists.newArrayList(
      new ColumnDesc("TABLE_CAT", "STRING", 1, "Catalog name. NULL if not applicable"),
      new ColumnDesc("TABLE_SCHEM", "STRING", 2, "Schema name"),
      new ColumnDesc("TABLE_NAME", "STRING", 3, "Table name"),
      new ColumnDesc("COLUMN_NAME", "STRING", 4, "Column name"),
      new ColumnDesc("DATA_TYPE", "INT", 5, "SQL type from java.sql.Types"),
      new ColumnDesc("TYPE_NAME", "STRING", 6, "Data source dependent type name, " +
        "for a UDT the type name is fully qualified"),
      new ColumnDesc("COLUMN_SIZE", "INT", 7, "Column size. For char or date types" +
        " this is the maximum number of characters, for numeric or decimal" +
        " types this is precision."),
      new ColumnDesc("BUFFER_LENGTH", "TINYINT", 8, "Unused"),
      new ColumnDesc("DECIMAL_DIGITS", "INT", 9, "The number of fractional digits"),
      new ColumnDesc("NUM_PREC_RADIX", "INT", 10, "Radix (typically either 10 or 2)"),
      new ColumnDesc("NULLABLE", "INT", 11, "Is NULL allowed"),
      new ColumnDesc("REMARKS", "STRING", 12, "Comment describing column (may be null)"),
      new ColumnDesc("COLUMN_DEF", "STRING", 13, "Default value (may be null)"),
      new ColumnDesc("SQL_DATA_TYPE", "INT", 14, "Unused"),
      new ColumnDesc("SQL_DATETIME_SUB", "INT", 15, "Unused"),
      new ColumnDesc("CHAR_OCTET_LENGTH", "INT", 16,
                     "For char types the maximum number of bytes in the column"),
      new ColumnDesc("ORDINAL_POSITION", "INT", 17, "Index of column in table (starting at 1)"),
      new ColumnDesc("IS_NULLABLE", "STRING", 18, "\"NO\" means column definitely does not " +
        "allow NULL values; \"YES\" means the column might allow NULL values. " +
        "An empty string means nobody knows."),
      new ColumnDesc("SCOPE_CATALOG", "STRING", 19, "Catalog of table that is the scope " +
        "of a reference attribute (null if DATA_TYPE isn't REF)"),
      new ColumnDesc("SCOPE_SCHEMA", "STRING", 20, "Schema of table that is the scope of a " +
        "reference attribute (null if the DATA_TYPE isn't REF)"),
      new ColumnDesc("SCOPE_TABLE", "STRING", 21, "Table name that this the scope " +
        "of a reference attribure (null if the DATA_TYPE isn't REF)"),
      new ColumnDesc("SOURCE_DATA_TYPE", "SMALLINT", 22, "Source type of a distinct type " +
        "or user-generated Ref type, SQL type from java.sql.Types " +
        "(null if DATA_TYPE isn't DISTINCT or user-generated REF)"),
      new ColumnDesc("IS_AUTO_INCREMENT", "STRING", 23,
                     "Indicates whether this column is auto incremented.")
    );

    // Get all columns
    ListenableFuture<ExploreExecutionResult> future = getExploreClient().columns(null, null, "%", "%");
    List<QueryResult> expectedColumns = Lists.newArrayList(getExpectedColumns(NAMESPACE_DATABASE));
    expectedColumns.addAll(getExpectedColumns(OTHER_NAMESPACE_DATABASE));
    assertStatementResult(future, true,
                          expectedColumnDescs, expectedColumns);

    // Get all columns in a namespace
    future = getExploreClient().columns(null, OTHER_NAMESPACE_ID.getNamespace(), "%", "%");
    assertStatementResult(future, true,
                          expectedColumnDescs,
                          getExpectedColumns(OTHER_NAMESPACE_DATABASE)
    );
  }

  private List<QueryResult> getExpectedColumns(String database) {
    return Lists.newArrayList(
      new QueryResult(Lists.<Object>newArrayList(
        null, database, MY_TABLE_NAME, "key", 12, "STRING",
        2147483647, null, null, null, 1,
        "from deserializer", null, null, null, null, 1,
        "YES", null, null, null, null, "NO")),
      new QueryResult(Lists.<Object>newArrayList(
        null, database, MY_TABLE_NAME, "value", 2002,
        "struct<name:string,ints:array<int>>", null, null,
        null, null, 1, "from deserializer", null, null,
        null, null, 2, "YES", null, null, null, null,
        "NO")),
      new QueryResult(Lists.<Object>newArrayList(
        null, database, otherTableName, "key", 12, "STRING",
        2147483647, null, null, null, 1,
        "from deserializer", null, null, null, null, 1,
        "YES", null, null, null, null, "NO")),
      new QueryResult(Lists.<Object>newArrayList(
        null, database, otherTableName, "value", 2002,
        "struct<name:string,ints:array<int>>", null, null,
        null, null, 1, "from deserializer", null, null,
        null, null, 2, "YES", null, null, null, null,
        "NO")));
  }

  @Test
  public void testGetInfo() throws Exception {
    Assert.assertEquals("Hive", getExploreClient().info(MetaDataInfo.InfoType.SERVER_NAME).get().getStringValue());
    Assert.assertEquals(128, getExploreClient().info(MetaDataInfo.InfoType.MAX_TABLE_NAME_LEN).get().getIntValue());
  }

  @Test
  public void testGetTableTypes() throws Exception {
    ListenableFuture<ExploreExecutionResult> future = getExploreClient().tableTypes();
    assertStatementResult(future, true,
                          Lists.newArrayList(
                            new ColumnDesc("TABLE_TYPE", "STRING", 1, "Table type name.")
                          ),
                          Lists.newArrayList(
                            new QueryResult(Lists.<Object>newArrayList("TABLE")),
                            new QueryResult(Lists.<Object>newArrayList("TABLE")),
                            new QueryResult(Lists.<Object>newArrayList("VIEW")),
                            new QueryResult(Lists.<Object>newArrayList("INDEX_TABLE"))
                          )
    );
  }

}
