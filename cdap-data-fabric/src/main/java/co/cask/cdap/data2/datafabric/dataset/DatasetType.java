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

package co.cask.cdap.data2.datafabric.dataset;

import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.IncompatibleUpdateException;
import co.cask.cdap.api.dataset.lib.AbstractDatasetDefinition;

import java.io.IOException;
import java.util.Map;

/**
 * Provides access to {@link DatasetDefinition} while removing burden of managing classloader separatelly.
 * @param <D> type of {@link Dataset} that {@link co.cask.cdap.api.dataset.DatasetDefinition} creates
 * @param <A> type of {@link DatasetAdmin} that {@link co.cask.cdap.api.dataset.DatasetDefinition} creates
 */
public final class DatasetType<D extends Dataset, A extends DatasetAdmin> {

  private final DatasetDefinition<D, A> delegate;
  private final ClassLoader classLoader;

  public DatasetType(DatasetDefinition<D, A> delegate, ClassLoader classLoader) {
    this.delegate = delegate;
    this.classLoader = classLoader;
  }

  public DatasetSpecification configure(String instanceName, DatasetProperties properties) {
    DatasetSpecification spec = delegate.configure(instanceName, properties);
    spec = spec.setOriginalProperties(properties);
    if (properties.getDescription() != null) {
      spec = spec.setDescription(properties.getDescription());
    }
    return spec;
  }

  public DatasetSpecification reconfigure(String instanceName,
                                          DatasetProperties newProperties,
                                          DatasetSpecification currentSpec) throws IncompatibleUpdateException {
    DatasetSpecification spec = AbstractDatasetDefinition
      .reconfigure(delegate, instanceName, newProperties, currentSpec)
      .setOriginalProperties(newProperties);
    if (newProperties.getDescription() != null) {
      spec = spec.setDescription(newProperties.getDescription());
    }
    return spec;
  }

  public A getAdmin(DatasetContext datasetContext, DatasetSpecification spec) throws IOException {
    return delegate.getAdmin(datasetContext, spec, classLoader);
  }

  public D getDataset(DatasetContext datasetContext, DatasetSpecification spec,
                      Map<String, String> arguments) throws IOException {
    return delegate.getDataset(datasetContext, spec, arguments, classLoader);
  }
}
