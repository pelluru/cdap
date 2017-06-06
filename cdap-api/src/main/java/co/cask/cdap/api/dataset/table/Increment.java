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

import java.util.Map;
import java.util.TreeMap;

/**
 * An Increment interprets the values of columns as 8-byte integers and
 * increments them by the specified value.
 * <ul>
 *   <li>
 *     The operation fails if a column's existing value is
 *     not exactly 8 bytes long (with {@link NumberFormatException}).
 *   </li><li>
 *     If a column to increment does not exist prior to the operation, then the column is created and 
 *     the column's value is set to the increment value.
 *   </li><li>
 *     An increment operation should at least change the value of one column.
 *   </li>
 * </ul>
 */
public class Increment {
  /** 
   * Row to change. 
   */
  private final byte[] row;
  /** 
   * Map of columns/values to increment each column's values by.
   */
  private final Map<byte[], Long> values;

  /**
   * @return Row to change.
   */
  public byte[] getRow() {
    return row;
  }

  /**
   * @return Map of columns/values to increment each column's values by.
   */
  public Map<byte[], Long> getValues() {
    return values;
  }

  // key as byte[]

  /**
   * Changes the values of columns in a row.
   * @param row Row to change.
   */
  public Increment(byte[] row) {
    this.row = row;
    this.values = new TreeMap<>(Bytes.BYTES_COMPARATOR);
  }

  /**
   * Changes the value of at least one column in a row.
   * @param row Row to change.
   * @param column Column to change.
   * @param value Value to increment by.
   */
  public Increment(byte[] row, byte[] column, long value) {
    this(row);
    add(column, value);
  }

  /**
   * Adds a column and sets the column's value.
   * @param column Column to add.
   * @param value Column value.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Increment}.
   */
  public Increment add(byte[] column, long value) {
    values.put(column, value);
    return this;
  }

  // key & column as String

  /**
   * Changes the values of all of the columns in a row.
   * @param row Row in which all column values are incremented.
   */
  public Increment(String row) {
    this(Bytes.toBytes(row));
  }

  /**
   * Changes the value of at least one column in a row.
   * @param row Row to change.
   * @param column Column to change.
   * @param value Value to increment the column value by.
   */
  public Increment(String row, String column, long value) {
    this(Bytes.toBytes(row));
    add(column, value);
  }

  /**
   * Adds a column and sets the column's value.
   * @param column Column to add.
   * @param value Column value.
   * @return Instance of this {@link co.cask.cdap.api.dataset.table.Increment}.
   */
  public Increment add(String column, long value) {
    return add(Bytes.toBytes(column), value);
  }
}
