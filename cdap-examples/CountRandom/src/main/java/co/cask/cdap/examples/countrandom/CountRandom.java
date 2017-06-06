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
package co.cask.cdap.examples.countrandom;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.lib.KeyValueTable;

/**
 * CountRandomDemo application contains a Flow {@code CountRandom}.
 */
public class CountRandom extends AbstractApplication {

  public static final String TABLE_NAME = "randomTable";

  @Override
  public void configure() {
    setName("CountRandom");
    setDescription("Example random count application");
    createDataset(TABLE_NAME, KeyValueTable.class, DatasetProperties.builder().setDescription("Counts table").build());
    addFlow(new CountRandomFlow());
  }
}
