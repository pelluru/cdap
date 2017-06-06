/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.api.dataset;

import co.cask.cdap.api.annotation.Beta;

/**
 * Provides access to the context for a dataset including its environment and configuration
 */
@Beta
public class DatasetContext {

  private final String namespaceId;

  private DatasetContext(String namespaceId) {
    this.namespaceId = namespaceId;
  }

  /**
   * Returns the namespace for a dataset
   *
   * @return the dataset's namespace
   */
  public String getNamespaceId() {
    return namespaceId;
  }

  @Override
  public String toString() {
    return "DatasetContext{" +
      "namespaceId='" + namespaceId + '\'' +
      '}';
  }

  /**
   * Constructs a new {@link DatasetContext} containing the specified namespace id
   *
   * @param namespaceId the namespace id to construct the {@link DatasetContext} with
   * @return a new {@link DatasetContext} containing the specified namespace id
   */
  public static DatasetContext from(String namespaceId) {
    if (namespaceId == null) {
      throw new IllegalArgumentException("Namespace Id can not be null");
    }
    return new DatasetContext(namespaceId);
  }
}
