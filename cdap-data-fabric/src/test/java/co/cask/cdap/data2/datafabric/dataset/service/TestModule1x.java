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

package co.cask.cdap.data2.datafabric.dataset.service;

import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.lib.AbstractDatasetDefinition;
import co.cask.cdap.api.dataset.lib.CompositeDatasetAdmin;
import co.cask.cdap.api.dataset.module.DatasetDefinitionRegistry;
import co.cask.cdap.api.dataset.module.DatasetModule;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Test dataset module
 */
public class TestModule1x implements DatasetModule {
  @Override
  public void register(DatasetDefinitionRegistry registry) {
    registry.add(createDefinition("datasetType1"));
    registry.add(createDefinition("datasetType1x"));
  }
  private DatasetDefinition createDefinition(String name) {
    return new AbstractDatasetDefinition(name) {
      @Override
      public DatasetSpecification configure(String instanceName, DatasetProperties properties) {
        return DatasetSpecification
          .builder(instanceName, getName())
          .properties(properties.getProperties())
          .build();
      }

      @Override
      public DatasetAdmin getAdmin(DatasetContext datasetContext, DatasetSpecification spec,
                                   ClassLoader classLoader) {
        return new CompositeDatasetAdmin(Collections.<String, DatasetAdmin>emptyMap());
      }

      @Override
      public Dataset getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                                Map arguments, ClassLoader classLoader) throws IOException {
        return null;
      }
    };
  }
}


