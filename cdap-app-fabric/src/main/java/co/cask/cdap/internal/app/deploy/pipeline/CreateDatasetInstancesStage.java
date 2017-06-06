/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package co.cask.cdap.internal.app.deploy.pipeline;

import co.cask.cdap.api.app.ApplicationSpecification;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.pipeline.AbstractStage;
import com.google.common.reflect.TypeToken;

/**
 * This {@link co.cask.cdap.pipeline.Stage} is responsible for automatic
 * deploy of the {@link co.cask.cdap.api.dataset.module.DatasetModule}s specified by application.
 */
public class CreateDatasetInstancesStage extends AbstractStage<ApplicationDeployable> {
  private final DatasetInstanceCreator datasetInstanceCreator;

  public CreateDatasetInstancesStage(CConfiguration configuration, DatasetFramework datasetFramework) {
    super(TypeToken.of(ApplicationDeployable.class));
    this.datasetInstanceCreator = new DatasetInstanceCreator(configuration, datasetFramework);
  }

  /**
   * Receives an input containing application specification and location
   * and verifies both.
   *
   * @param input An instance of {@link ApplicationDeployable}
   */
  @Override
  public void process(ApplicationDeployable input) throws Exception {
    // create dataset instances
    ApplicationSpecification specification = input.getSpecification();
    datasetInstanceCreator.createInstances(input.getApplicationId().getParent(), specification.getDatasets(),
                                           input.getOwnerPrincipal());

    // Emit the input to next stage.
    emit(input);
  }
}
