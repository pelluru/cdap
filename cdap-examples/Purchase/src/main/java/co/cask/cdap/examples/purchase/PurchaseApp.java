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

package co.cask.cdap.examples.purchase;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.app.ProgramType;
import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.dataset.lib.ObjectMappedTable;
import co.cask.cdap.api.dataset.lib.ObjectMappedTableProperties;
import co.cask.cdap.api.schedule.Schedules;

/**
 * This implements a simple purchase history application via a scheduled MapReduce Workflow --
 * see package-info for more details.
 */
public class PurchaseApp extends AbstractApplication {

  public static final String APP_NAME = "PurchaseHistory";

  @Override
  public void configure() {
    setName(APP_NAME);
    setDescription("Purchase history application");

    // Ingest data into the Application via a Stream
    addStream(new Stream("purchaseStream"));

    // Store processed data in a Dataset
    createDataset("frequentCustomers", KeyValueTable.class,
                  DatasetProperties.builder().setDescription("Store frequent customers").build());

    // Store user profiles in a Dataset
    createDataset("userProfiles", KeyValueTable.class,
                  DatasetProperties.builder().setDescription("Store user profiles").build());

    // Process events in realtime using a Flow
    addFlow(new PurchaseFlow());

    // Specify a MapReduce to run on the acquired data
    addMapReduce(new PurchaseHistoryBuilder());

    // Run a Workflow that uses the MapReduce to run on the acquired data
    addWorkflow(new PurchaseHistoryWorkflow());

    // Retrieve the processed data using a Service
    addService(new PurchaseHistoryService());

    // Store and retrieve user profile data using a Service
    addService(UserProfileServiceHandler.SERVICE_NAME, new UserProfileServiceHandler());

    // Provide a Service to Application components
    addService(new CatalogLookupService());

    // Schedule the workflow
    schedule(
      buildSchedule("DailySchedule", ProgramType.WORKFLOW, "PurchaseHistoryWorkflow")
      .withConcurrency(1)
      .triggerByTime("0 4 * * *")
    );

    // Schedule the workflow based on the data coming in the purchaseStream stream
    scheduleWorkflow(
      Schedules.builder("DataSchedule")
        .setDescription("Schedule execution when 1 MB or more of data is ingested in the purchaseStream")
        .setMaxConcurrentRuns(1)
        .createDataSchedule(Schedules.Source.STREAM, "purchaseStream", 1),
      "PurchaseHistoryWorkflow"
    );

    createDataset("history", PurchaseHistoryStore.class, PurchaseHistoryStore.properties("History dataset"));
    try {
      createDataset("purchases", ObjectMappedTable.class, ObjectMappedTableProperties.builder().setType(Purchase.class)
        .setDescription("Store purchases").build());
    } catch (UnsupportedTypeException e) {
      // This exception is thrown by ObjectMappedTable if its parameter type cannot be
      // (de)serialized (for example, if it is an interface and not a class, then there is
      // no auto-magic way deserialize an object.) In this case that will not happen
      // because PurchaseHistory and Purchase are actual classes.
      throw new RuntimeException(e);
    }
  }
}
