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

package co.cask.cdap.explore.utils;

import com.google.common.base.Objects;

/**
 * Class to represent a JSON object passed as an argument to the getColumns metadata HTTP endpoint.
 */
public final class ColumnsArgs {
  private final String catalog;
  private final String schemaPattern;
  private final String tableNamePattern;
  private final String columnNamePattern;

  public ColumnsArgs(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) {
    this.catalog = catalog;
    this.schemaPattern = schemaPattern;
    this.tableNamePattern = tableNamePattern;
    this.columnNamePattern = columnNamePattern;
  }

  public String getTableNamePattern() {
    return tableNamePattern;
  }

  public String getColumnNamePattern() {
    return columnNamePattern;
  }

  public String getSchemaPattern() {
    return schemaPattern;
  }

  public String getCatalog() {
    return catalog;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("catalog", catalog)
      .add("schemaPattern", schemaPattern)
      .add("tableNamePattern", tableNamePattern)
      .add("columnNamePattern", columnNamePattern)
      .toString();
  }
}
