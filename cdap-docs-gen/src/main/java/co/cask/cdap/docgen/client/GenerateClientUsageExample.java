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

package co.cask.cdap.docgen.client;

import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.api.service.ServiceSpecification;
import co.cask.cdap.client.ApplicationClient;
import co.cask.cdap.client.DatasetClient;
import co.cask.cdap.client.DatasetModuleClient;
import co.cask.cdap.client.DatasetTypeClient;
import co.cask.cdap.client.MetricsClient;
import co.cask.cdap.client.MonitorClient;
import co.cask.cdap.client.PreferencesClient;
import co.cask.cdap.client.ProgramClient;
import co.cask.cdap.client.QueryClient;
import co.cask.cdap.client.ServiceClient;
import co.cask.cdap.client.StreamClient;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.explore.client.ExploreExecutionResult;
import co.cask.cdap.proto.ApplicationRecord;
import co.cask.cdap.proto.ColumnDesc;
import co.cask.cdap.proto.DatasetModuleMeta;
import co.cask.cdap.proto.DatasetSpecificationSummary;
import co.cask.cdap.proto.DatasetTypeMeta;
import co.cask.cdap.proto.StreamProperties;
import co.cask.cdap.proto.SystemServiceMeta;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.DatasetModuleId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Generates cdap-docs/reference-manual/source/java-client-api.rst.
 */
public class GenerateClientUsageExample {

  private final ClientConfig clientConfig = null;

  public void applicationClient() throws Exception {
    // Construct the client used to interact with CDAP
    ApplicationClient appClient = new ApplicationClient(clientConfig);

    // Fetch the list of applications
    List<ApplicationRecord> apps = appClient.list(NamespaceId.DEFAULT);

    // Deploy an application
    File appJarFile = new File("your-app.jar");
    appClient.deploy(NamespaceId.DEFAULT, appJarFile);

    // Delete an application
    appClient.delete(NamespaceId.DEFAULT.app("Purchase"));

    // List programs belonging to an application
    appClient.listPrograms(NamespaceId.DEFAULT.app("Purchase"));
  }

  public void preferencesClient() throws Exception {
    // Construct the client used to interact with CDAP
    PreferencesClient preferencesClient = new PreferencesClient(clientConfig);

    Map<String, String> propMap = Maps.newHashMap();
    propMap.put("k1", "v1");

    // Set preferences at the Instance level
    preferencesClient.setInstancePreferences(propMap);

    // Get preferences at the Instance level
    preferencesClient.getInstancePreferences();

    // Delete preferences at the Instance level
    preferencesClient.deleteInstancePreferences();

    // Set preferences of MyApp application which is deployed in the Dev namespace
    preferencesClient.setApplicationPreferences(new ApplicationId("Dev", "MyApp"), propMap);

    // Get only the preferences of MyApp application which is deployed in the Dev namespace
    Map<String, String> appPrefs = preferencesClient.getApplicationPreferences(
      new ApplicationId("Dev", "MyApp"), false);

    // Get the resolved preferences (collapsed with higher level(s) of preferences)
    Map<String, String> resolvedAppPrefs = preferencesClient.getApplicationPreferences(
      new ApplicationId("Dev", "MyApp"), true);
  }

  public void programClient() throws Exception {
    // Construct the client used to interact with CDAP
    ProgramClient programClient = new ProgramClient(clientConfig);

    // Start a service in the WordCount example
    programClient.start(NamespaceId.DEFAULT.app("WordCount").service("RetrieveCounts"));

    // Fetch live information from the HelloWorld example
    // Live info includes the address of an component’s container host and the container’s debug port,
    // formatted in JSON
    programClient.getLiveInfo(NamespaceId.DEFAULT.app("HelloWorld").service("greet"));

    // Fetch program logs in the WordCount example
    programClient.getProgramLogs(NamespaceId.DEFAULT.app("WordCount").service("RetrieveCounts"), 0, Long.MAX_VALUE);

    // Scale a service in the HelloWorld example
    programClient.setServiceInstances(NamespaceId.DEFAULT.app("HelloWorld").service("greet"), 3);

    // Stop a service in the HelloWorld example
    programClient.stop(NamespaceId.DEFAULT.app("HelloWorld").service("greet"));

    // Start, scale, and stop a flow in the WordCount example
    programClient.start(NamespaceId.DEFAULT.app("WordCount").flow("WordCountFlow"));

    // Fetch the last 10 flow runs in the WordCount example
    programClient.getAllProgramRuns(NamespaceId.DEFAULT.app("WordCount").flow("WordCountFlow"), 0, Long.MAX_VALUE, 10);

    // Scale a flowlet in the WordCount example
    programClient.setFlowletInstances(
      NamespaceId.DEFAULT.app("WordCount").flow("WordCountFlow").flowlet("Tokenizer"),
      3);

    // Stop a flow in the WordCount example
    programClient.stop(NamespaceId.DEFAULT.app("WordCount").flow("WordCountFlow"));
  }

  public void streamClient() throws Exception {
    // Construct the client used to interact with CDAP
    StreamClient streamClient = new StreamClient(clientConfig);

    // Fetch the stream list
    List streams = streamClient.list(NamespaceId.DEFAULT);

    // Create a stream, using the Purchase example
    StreamId streamId = NamespaceId.DEFAULT.stream("purchases");
    streamClient.create(streamId);

    // Fetch a stream's properties
    StreamProperties config = streamClient.getConfig(streamId);

    // Send events to a stream
    streamClient.sendEvent(streamId, "Tom bought 5 apples for $10");

    // Read all events from a stream (results in events)
    List<StreamEvent> events = Lists.newArrayList();
    streamClient.getEvents(streamId, 0, Long.MAX_VALUE, Integer.MAX_VALUE, events);

    // Read first 5 events from a stream (results in events)
    events = Lists.newArrayList();
    streamClient.getEvents(streamId, 0, Long.MAX_VALUE, 5, events);

    // Read 2nd and 3rd events from a stream, after first calling getEvents
    long startTime = events.get(1).getTimestamp();
    long endTime = events.get(2).getTimestamp() + 1;
    events.clear();
    streamClient.getEvents(streamId, startTime, endTime, Integer.MAX_VALUE, events);

    // Write asynchronously to a stream
    streamId = NamespaceId.DEFAULT.stream("testAsync");
    events = Lists.newArrayList();

    streamClient.create(streamId);

    // Send 10 async writes
    int msgCount = 10;
    for (int i = 0; i < msgCount; i++) {
      streamClient.asyncSendEvent(streamId, "Testing " + i);
    }

    // Read them back; need to read it multiple times as the writes happen asynchronously
    while (events.size() != msgCount) {
      events.clear();
      streamClient.getEvents(streamId, 0, Long.MAX_VALUE, msgCount, events);
    }

    // Check that there are no more events
    events.clear();
    while (events.isEmpty()) {
      events.clear();
      streamClient.getEvents(streamId, 0, Long.MAX_VALUE, msgCount, events);
    }
    // End write asynchronously
  }

  public void datasetClient() throws Exception {
    // Construct the client used to interact with CDAP
    DatasetClient datasetClient = new DatasetClient(clientConfig);

    // Fetch the list of datasets
    List<DatasetSpecificationSummary> datasets = datasetClient.list(NamespaceId.DEFAULT);

    // Create a dataset
    DatasetId datasetId = NamespaceId.DEFAULT.dataset("someDataset");
    datasetClient.create(datasetId, "someDatasetType");

    // Truncate a dataset
    datasetClient.truncate(datasetId);

    // Delete a dataset
    datasetClient.delete(datasetId);
  }

  public void datasetModuleClient() throws Exception {
    // Construct the client used to interact with CDAP
    DatasetModuleClient datasetModuleClient = new DatasetModuleClient(clientConfig);

    // Add a dataset module
    File moduleJarFile = createAppJarFile(SomeDatasetModule.class);
    DatasetModuleId datasetModuleId = NamespaceId.DEFAULT.datasetModule("someDatasetModule");
    datasetModuleClient.add(datasetModuleId, SomeDatasetModule.class.getName(), moduleJarFile);

    // Fetch the dataset module information
    DatasetModuleMeta datasetModuleMeta = datasetModuleClient.get(datasetModuleId);

    // Delete all dataset modules
    datasetModuleClient.deleteAll(NamespaceId.DEFAULT);
  }

  public void datasetTypeClient() throws Exception {
    // Construct the client used to interact with CDAP
    DatasetTypeClient datasetTypeClient = new DatasetTypeClient(clientConfig);

    // Fetch the dataset type information using the type name
    DatasetTypeMeta datasetTypeMeta = datasetTypeClient.get(NamespaceId.DEFAULT.datasetType("someDatasetType"));

    // Fetch the dataset type information using the classname
    datasetTypeClient.get(NamespaceId.DEFAULT.datasetType(SomeDataset.class.getName()));
  }

  public void queryClient() throws Exception {
    // Construct the client used to interact with CDAP
    QueryClient queryClient = new QueryClient(clientConfig);

    // Perform an ad-hoc query using the Purchase example
    ListenableFuture<ExploreExecutionResult> resultFuture = queryClient.execute(
      NamespaceId.DEFAULT, "SELECT * FROM dataset_history WHERE customer IN ('Alice','Bob')");
    ExploreExecutionResult results = resultFuture.get();

    // Fetch schema
    List<ColumnDesc> schema = results.getResultSchema();
    String[] header = new String[schema.size()];
    for (int i = 0; i < header.length; i++) {
      ColumnDesc column = schema.get(i);
      // Hive columns start at 1
      int index = column.getPosition() - 1;
      header[index] = column.getName() + ": " + column.getType();
    }
  }

  public void serviceClient() throws Exception {
    // Construct the client used to interact with CDAP
    ServiceClient serviceClient = new ServiceClient(clientConfig);

    // Fetch service information using the service in the PurchaseApp example
    ServiceSpecification serviceSpec = serviceClient.get(
      NamespaceId.DEFAULT.app("PurchaseApp").service("CatalogLookup"));
  }

  public void metricsClient() throws Exception {
    // Construct the client used to interact with CDAP
    MetricsClient metricsClient = new MetricsClient(clientConfig);

    // Fetch the total number of events that have been processed by a flowlet
    RuntimeMetrics metric = metricsClient.getFlowletMetrics(
      new NamespaceId("user").app("HelloWorld").flow("someFlow").flowlet("process.events.processed"));
  }

  public void monitorClient() throws Exception {
    // Construct the client used to interact with CDAP
    MonitorClient monitorClient = new MonitorClient(clientConfig);

    // Fetch the list of system services
    List<SystemServiceMeta> services = monitorClient.listSystemServices();

    // Fetch status of system transaction service
    String serviceStatus = monitorClient.getSystemServiceStatus("transaction");

    // Fetch the number of instances of the system transaction service
    int systemServiceInstances = monitorClient.getSystemServiceInstances("transaction");

    // Set the number of instances of the system transaction service
    monitorClient.setSystemServiceInstances("transaction", 1);
  }

  private File createAppJarFile(Class<?> cls) {
    return null;
  }

  /**
   *
   */
  private class SomeDatasetModule {

  }

  /**
   *
   */
  private class SomeDataset {

  }
}
