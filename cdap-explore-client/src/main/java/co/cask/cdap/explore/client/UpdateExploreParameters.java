/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.explore.client;

import co.cask.cdap.api.dataset.DatasetSpecification;

/**
 * This class represents the body of an HTTP request to update a dataset in Explore.
 */
public class UpdateExploreParameters {

  private final DatasetSpecification oldSpec;
  private final DatasetSpecification newSpec;

  public UpdateExploreParameters(DatasetSpecification oldSpec, DatasetSpecification newSpec) {
    this.oldSpec = oldSpec;
    this.newSpec = newSpec;
  }

  public DatasetSpecification getOldSpec() {
    return oldSpec;
  }

  public DatasetSpecification getNewSpec() {
    return newSpec;
  }
}
