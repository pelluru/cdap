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

package co.cask.cdap.api.dataset.table;

import java.util.Collection;

/**
 * A Delete removes one, multiple, or all columns from a row.
 */
public class Delete extends RowColumns<Delete> {
  /**
   * Delete whole row.
   * NOTE: depending on implementation this operation may be less efficient than calling delete with set of columns
   * @param row Row to delete.
   */
  public Delete(byte[] row) {
    super(row);
  }

  /**
   * Delete a set of columns from a row.
   * @param row Row to delete from.
   * @param columns Columns to delete.
   */
  public Delete(byte[] row, byte[]... columns) {
    super(row, columns);
  }

  /**
   * Delete a set of columns from a row.
   * @param row Row to delete from.
   * @param columns Columns to delete.
   */
  public Delete(byte[] row, Collection<byte[]> columns) {
    super(row, columns.toArray(new byte[columns.size()][]));
  }

  /**
   * Delete a whole row.
   * NOTE: depending on the implementation, this operation may be less efficient than 
   * calling delete with a set of columns.
   * @param row row to delete
   */
  public Delete(String row) {
    super(row);
  }

  /**
   * Delete a set of columns from a row.
   * @param row Row to delete from.
   * @param columns Columns to delete.
   */
  public Delete(String row, String... columns) {
    super(row, columns);
  }

  /**
   * Delete a set of columns from a row.
   * @param row Row to delete from.
   * @param columns Columns to delete.
   */
  public Delete(String row, Collection<String> columns) {
    super(row, columns.toArray(new String[columns.size()]));
  }
}

