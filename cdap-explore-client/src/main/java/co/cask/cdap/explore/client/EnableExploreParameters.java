/*
 * Copyright © 2016-2017 Cask Data, Inc.
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
 * This class represents the body of an HTTP request to enable a dataset for Explore.
 */
public class EnableExploreParameters {

  private final DatasetSpecification spec;
  private final boolean truncating;

  public EnableExploreParameters(DatasetSpecification newSpec, boolean truncating) {
    this.spec = newSpec;
    this.truncating = truncating;
  }

  public DatasetSpecification getSpec() {
    return spec;
  }

  public boolean isTruncating() {
    return truncating;
  }
}
