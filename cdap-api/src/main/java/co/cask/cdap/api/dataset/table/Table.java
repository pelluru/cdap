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

package co.cask.cdap.api.dataset.table;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.data.batch.BatchReadable;
import co.cask.cdap.api.data.batch.BatchWritable;
import co.cask.cdap.api.data.batch.RecordScannable;
import co.cask.cdap.api.data.batch.RecordWritable;
import co.cask.cdap.api.data.batch.Split;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetProperties;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An ordered, optionally explorable, named table.
 */
public interface Table extends BatchReadable<byte[], Row>, BatchWritable<byte[], Put>,
  Dataset, RecordScannable<StructuredRecord>, RecordWritable<StructuredRecord> {

  /**
   * Property set to configure time-to-live on data within this dataset. The value given is in seconds.
   * Once a cell's data has surpassed the given value in age,
   * the cell's data will no longer be visible and may be garbage collected.
   */
  String PROPERTY_TTL = "dataset.table.ttl";

  /**
   * Property set to configure read-less increment support for a dataset.  When not set, calling the
   * {@link Table#increment(byte[], byte[], long)} method will result in a normal read-modify-write operation.
   */
  String PROPERTY_READLESS_INCREMENT = "dataset.table.readless.increment";

  /**
   * Property set to configure name of the column family. This property only applies to implementations that support
   * it. If not set, a default column family will be used.
   */
  String PROPERTY_COLUMN_FAMILY = "dataset.table.column.family";

  /**
   * The default column family. This is used if table properties do not specify a column family.
   */
  String DEFAULT_COLUMN_FAMILY = "d";

  /**
   * Property set to configure transaction conflict detection level. This property only applies to implementations
   * that support transaction. The property value must be obtained by {@link ConflictDetection#name()}.
   *
   * @see ConflictDetection
   */
  String PROPERTY_CONFLICT_LEVEL = "conflict.level";

  /**
   * Property set to configure schema for the table. Schema is currently not enforced when writing to a Table,
   * but is used when exploring a Table. Field names from the schema will be read from columns of the same name.
   * For example, if the schema contains an integer field named "count", the value for that field will be read
   * from the "count" column. The schema can only contain simple types.
   */
  String PROPERTY_SCHEMA = DatasetProperties.SCHEMA;

  /**
   * Property set to configure which field in the schema is the row key.
   */
  String PROPERTY_SCHEMA_ROW_FIELD = "schema.row.field";

  /**
   * Reads values of all columns of the specified row.
   * <p>
   * NOTE: Depending on the implementation of this interface and use-case, calling this method can be less
   * efficient than calling the same method with columns as parameters because it can require making a
   * round trip to the persistent store.
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param row row to read from
   * @return instance of {@link Row}: never {@code null}; returns an empty Row if nothing read
   */
  @Nonnull
  Row get(byte[] row);

  /**
   * Reads the value of the specified column of the specified row.
   *
   * @param row row to read from
   * @param column column to read value for
   * @return value of the column or {@code null} if the value is absent
   */
  byte[] get(byte[] row, byte[] column);

  /**
   * Reads the values of the specified columns of the specified row.
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param row row to read from
   * @param columns collection of columns to read; if empty, will return an empty Row.
   * @return instance of {@link Row}: never {@code null}; returns an empty Row if nothing read
   */
  @Nonnull
  Row get(byte[] row, byte[][] columns);

  /**
   * Reads the values of all columns in the specified row that are
   * between the specified start (inclusive) and stop (exclusive) columns.
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param startColumn beginning of range of columns, inclusive
   * @param stopColumn end of range of columns, exclusive
   * @param limit maximum number of columns to return
   * @return instance of {@link Row}; never {@code null}; returns an empty Row if nothing read
   */
  @Nonnull
  Row get(byte[] row, byte[] startColumn, byte[] stopColumn, int limit);

  /**
   * Reads values of columns as defined by {@link Get} parameter.
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param get defines read selection
   * @return instance of {@link Row}: never {@code null}; returns an empty Row if nothing read
   */
  @Nonnull
  Row get(Get get);

  /**
   * Reads values for the rows and columns defined by the {@link Get} parameters.  When running in distributed mode,
   * and retrieving multiple rows at the same time, this method should be preferred to multiple {@link Table#get(Get)}
   * calls, as the operations will be batched into a single remote call per server.
   *
   * @param gets defines the rows and columns to read
   * @return a list of {@link Row} instances
   */
  List<Row> get(List<Get> gets);

  /**
   * Writes the specified value for the specified column of the specified row.
   *
   * @param row row to write to
   * @param column column to write to
   * @param value to write
   */
  void put(byte[] row, byte[] column, byte[] value);

  /**
   * Writes the specified values for the specified columns of the specified row.
   * <p>
   * NOTE: Depending on the implementation, this can work faster than calling {@link #put(byte[], byte[], byte[])}
   * multiple times (especially in transactions that alter many columns of one row).
   *
   * @param row row to write to
   * @param columns columns to write to
   * @param values array of values to write (same order as values)
   */
  void put(byte[] row, byte[][] columns, byte[][] values);

  /**
   * Writes values into a column of a row as defined by the {@link Put} parameter.
   *
   * @param put defines values to write
   */
  void put(Put put);

  /**
   * Deletes all columns of the specified row.
   * <p>
   * NOTE: Depending on the implementation of this interface and use-case, calling this method can be less
   * efficient than calling the same method with columns as parameters because it can require a round trip to
   * the persistent store.
   *
   * @param row row to delete
   */
  void delete(byte[] row);

  /**
   * Deletes specified column of the specified row.
   *
   * @param row row to delete from
   * @param column column name to delete
   */
  void delete(byte[] row, byte[] column);

  /**
   * Deletes specified columns of the specified row.
   * <p>
   * NOTE: Depending on the implementation, this can work faster than calling {@link #delete(byte[], byte[])}
   * multiple times (especially in transactions that delete many columns of the same rows).
   *
   * @param row row to delete from
   * @param columns names of columns to delete; if empty, nothing will be deleted
   */
  void delete(byte[] row, byte[][] columns);

  /**
   * Deletes columns of a row as defined by the {@link Delete} parameter.
   *
   * @param delete defines what to delete
   */
  void delete(Delete delete);

  /**
   * Increments the specified column of the row by the specified amount and returns the new value.
   *
   * @param row row which value to increment
   * @param column column to increment
   * @param amount amount to increment by
   * @return new value of the column
   * @throws NumberFormatException if stored value for the column is not in the serialized long value format
   */
  long incrementAndGet(byte[] row, byte[] column, long amount);

  /**
   * Increments the specified columns of the row by the specified amounts and returns the new values.
   * <p>
   * NOTE: Depending on the implementation, this can work faster than calling
   * {@link #incrementAndGet(byte[], byte[], long)}
   * multiple times (especially in a transaction that increments multiple columns of the same rows)
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param row row whose values to increment
   * @param columns columns to increment
   * @param amounts amounts to increment columns by (in the same order as the columns)
   * @return {@link Row} with a subset of changed columns
   * @throws NumberFormatException if stored value for the column is not in the serialized long value format
   */
  Row incrementAndGet(byte[] row, byte[][] columns, long[] amounts);

  /**
   * Increments the specified columns of a row by the specified amounts defined by the {@link Increment} parameter and
   * returns the new values
   * <p>
   * NOTE: objects that are passed in parameters can be re-used by underlying implementation and present
   *       in returned data structures from this method.
   *
   * @param increment defines changes
   * @return {@link Row} with a subset of changed columns
   * @throws NumberFormatException if stored value for the column is not in the serialized long value format
   */
  Row incrementAndGet(Increment increment);

  /**
   * Increments (atomically) the specified row and columns by the specified amounts, without returning the new value.
   * @param row row which values to increment
   * @param column column to increment
   * @param amount amount to increment by
   */
  void increment(byte[] row, byte[] column, long amount);

  /**
   * Increments (atomically) the specified row and columns by the specified amounts, without returning the new values.
   *
   * NOTE: depending on the implementation this may work faster than calling
   * {@link #increment(byte[], byte[], long)} multiple times (esp. in transaction that changes a lot of rows)
   *  @param row row which values to increment
   * @param columns columns to increment
   * @param amounts amounts to increment columns by (same order as columns)
   */
  void increment(byte[] row, byte[][] columns, long[] amounts);

  /**
   * Increments (atomically) the specified row and columns by the specified amounts, without returning the new values.
   *
   * NOTE: depending on the implementation this may work faster than calling
   * {@link #increment(byte[], byte[], long)} multiple times (esp. in transaction that changes a lot of rows)
   *
   * @param increment the row and column increment amounts
   */
  void increment(Increment increment);

  /**
   * Scans table.
   *
   * @param startRow start row inclusive; {@code null} means start from first row of the table
   * @param stopRow stop row exclusive; {@code null} means scan all rows to the end of the table
   * @return instance of {@link Scanner}
   */
  Scanner scan(@Nullable byte[] startRow, @Nullable byte[] stopRow);

  /**
   * Returns a {@link Scanner} as specified by a given {@link Scan}.
   *
   * @param scan a {@link Scan} instance
   * @return instance of {@link Scanner}
   */
  @Beta
  Scanner scan(Scan scan);

  /**
   * Returns splits for a range of keys in the table.
   * 
   * @param numSplits Desired number of splits. If greater than zero, at most this many splits will be returned.
   *                  If less than or equal to zero, any number of splits can be returned.
   * @param start if non-null, the returned splits will only cover keys that are greater or equal
   * @param stop if non-null, the returned splits will only cover keys that are less
   * @return list of {@link Split}
   */
  List<Split> getSplits(int numSplits, @Nullable byte[] start, @Nullable byte[] stop);

  /**
   * Compares-and-swaps (atomically) the value of the specified row and column by looking for
   * an expected value and, if found, replacing it with the new value.
   *
   * @param key row to modify
   * @param keyColumn column to modify
   * @param oldValue expected value before change
   * @param newValue value to set
   * @return true if compare and swap succeeded, false otherwise (stored value is different from expected)
   */
  boolean compareAndSwap(byte[] key, byte[] keyColumn, byte[] oldValue, byte[] newValue);

  /**
   * Writes the {key, value} record into a dataset.
   *
   * @param key always ignored, and it is safe to write null as the key.
   * @param value a Put for one row of the table.
   */
  @Override
  void write(byte[] key, Put value);
}
