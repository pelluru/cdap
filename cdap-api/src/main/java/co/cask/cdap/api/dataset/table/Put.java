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

package co.cask.cdap.api.dataset.table;

import co.cask.cdap.api.common.Bytes;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the specified value(s) in one or more columns of a row -- this overrides existing values.
 */
public class Put implements Serializable {

  private static final long serialVersionUID = 5869452950547737896L;

  /** row to write to. */
  private final byte[] row;

  /** map of column to value to write. */
  private final Map<byte[], byte[]> values;

  /**
   * @return Row to write to.
   */
  public byte[] getRow() {
    return row;
  }

  /**
   * @return Map of column to value to write.
   */
  public Map<byte[], byte[]> getValues() {
    return values;
  }

  // key as byte[]

  /**
   * Write to a row.
   * @param row Row to write to.
   */
  public Put(byte[] row) {
    this.row = row;
    this.values = new TreeMap<>(Bytes.BYTES_COMPARATOR);
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, byte[] value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, byte[] value) {
    values.put(column, value);
    return this;
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, String value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, String value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, boolean value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, boolean value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, short value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, short value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, int value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, int value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, long value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, long value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, float value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column Column to write to.
   * @param value Value to write.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Put}.
   */
  public Put add(byte[] column, float value) {
    return add(column, Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row Row to write to.
   * @param column Column to write to.
   * @param value Value to write.
   */
  public Put(byte[] row, byte[] column, double value) {
    this(row);
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(byte[] column, double value) {
    return add(column, Bytes.toBytes(value));
  }

  // key & column as String

  /**
   * Write to a row.
   * @param row row to write to
   */
  public Put(String row) {
    this(Bytes.toBytes(row));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, byte[] value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, byte[] value) {
    return add(Bytes.toBytes(column), value);
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, String value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, String value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, boolean value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, boolean value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, short value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, short value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, int value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, int value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, long value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, long value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of a row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, float value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, float value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }

  /**
   * Write at least one value in a column of row.
   * @param row row to write to
   * @param column column to write to
   * @param value value to write
   */
  public Put(String row, String column, double value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Write a value to a column.
   * @param column column to write to
   * @param value value to write
   * @return instance of this {@link co.cask.cdap.api.dataset.table.Put}
   */
  public Put add(String column, double value) {
    return add(Bytes.toBytes(column), Bytes.toBytes(value));
  }
}
