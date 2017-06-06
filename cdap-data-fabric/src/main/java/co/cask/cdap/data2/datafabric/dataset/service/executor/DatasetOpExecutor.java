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

package co.cask.cdap.data2.datafabric.dataset.service.executor;

import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.proto.DatasetTypeMeta;
import co.cask.cdap.proto.id.DatasetId;
import com.google.common.util.concurrent.Service;

import java.io.IOException;

/**
 * Executes various Dataset operations.
 */
public interface DatasetOpExecutor extends Service {

  /**
   * Checks for the existence of a dataset instance
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @return true if dataset exists
   * @throws IOException
   */
  boolean exists(DatasetId datasetInstanceId) throws Exception;

  /**
   * Creates a dataset.
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @param typeMeta Data set type meta
   * @param props Data set instance properties
   * @throws IOException
   */
  DatasetSpecification create(DatasetId datasetInstanceId, DatasetTypeMeta typeMeta, DatasetProperties props)
    throws Exception;

  /**
   * Creates a dataset.
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @param typeMeta dataset type meta
   * @param props dataset instance properties
   * @param existing the existing dataset spec
   * @throws IOException
   */
  DatasetSpecification update(DatasetId datasetInstanceId, DatasetTypeMeta typeMeta,
                              DatasetProperties props, DatasetSpecification existing) throws Exception;

  /**
   * Drops dataset.
   *
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @param typeMeta Data set type meta
   * @param spec Data set instance spec
   * @throws IOException
   */
  void drop(DatasetId datasetInstanceId, DatasetTypeMeta typeMeta, DatasetSpecification spec) throws Exception;

  /**
   * Deletes all data of the dataset.
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @throws IOException
   */
  void truncate(DatasetId datasetInstanceId) throws Exception;

  /**
   * Upgrades dataset.
   *
   * @param datasetInstanceId {@link DatasetId} of the dataset instance.
   * @throws IOException
   */
  void upgrade(DatasetId datasetInstanceId) throws Exception;

}
