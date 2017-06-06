/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.internal.app.services.http.handlers;

import co.cask.cdap.AppWithMultipleScheduledWorkflows;
import co.cask.cdap.AppWithSchedule;
import co.cask.cdap.AppWithServices;
import co.cask.cdap.AppWithWorker;
import co.cask.cdap.AppWithWorkflow;
import co.cask.cdap.DummyAppWithTrackingTable;
import co.cask.cdap.SleepingWorkflowApp;
import co.cask.cdap.WordCountApp;
import co.cask.cdap.api.Config;
import co.cask.cdap.api.artifact.ArtifactSummary;
import co.cask.cdap.api.schedule.RunConstraints;
import co.cask.cdap.api.schedule.SchedulableProgramType;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.api.schedule.Schedules;
import co.cask.cdap.api.service.ServiceSpecification;
import co.cask.cdap.api.service.http.HttpServiceHandlerSpecification;
import co.cask.cdap.api.service.http.ServiceHttpEndpoint;
import co.cask.cdap.api.workflow.ScheduleProgramInfo;
import co.cask.cdap.api.workflow.WorkflowActionSpecification;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.queue.DequeueStrategy;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.queue.QueueConsumer;
import co.cask.cdap.data2.queue.QueueEntry;
import co.cask.cdap.data2.queue.QueueProducer;
import co.cask.cdap.gateway.handlers.ProgramLifecycleHttpHandler;
import co.cask.cdap.internal.app.ServiceSpecificationCodec;
import co.cask.cdap.internal.app.runtime.schedule.constraint.ConcurrencyConstraint;
import co.cask.cdap.internal.app.runtime.schedule.store.Schedulers;
import co.cask.cdap.internal.app.runtime.schedule.trigger.StreamSizeTrigger;
import co.cask.cdap.internal.app.runtime.schedule.trigger.TimeTrigger;
import co.cask.cdap.internal.app.services.http.AppFabricTestBase;
import co.cask.cdap.internal.schedule.TimeSchedule;
import co.cask.cdap.internal.schedule.constraint.Constraint;
import co.cask.cdap.proto.ApplicationDetail;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.Instances;
import co.cask.cdap.proto.ProgramRecord;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.ProtoConstraint;
import co.cask.cdap.proto.ProtoTrigger;
import co.cask.cdap.proto.RunRecord;
import co.cask.cdap.proto.ScheduleDetail;
import co.cask.cdap.proto.ScheduleUpdateDetail;
import co.cask.cdap.proto.ServiceInstances;
import co.cask.cdap.proto.artifact.AppRequest;
import co.cask.cdap.proto.codec.ScheduleSpecificationCodec;
import co.cask.cdap.proto.codec.WorkflowActionSpecificationCodec;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.test.SlowTests;
import co.cask.cdap.test.XSlowTests;
import co.cask.common.http.HttpMethod;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.tephra.TransactionAware;
import org.apache.tephra.TransactionExecutor;
import org.apache.tephra.TransactionExecutorFactory;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Tests for {@link ProgramLifecycleHttpHandler}
 */
public class ProgramLifecycleHttpHandlerTest extends AppFabricTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(ProgramLifecycleHttpHandlerTest.class);

  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(ScheduleSpecification.class, new ScheduleSpecificationCodec())
    .registerTypeAdapter(WorkflowActionSpecification.class, new WorkflowActionSpecificationCodec())
    .create();
  private static final Type LIST_OF_JSONOBJECT_TYPE = new TypeToken<List<JsonObject>>() { }.getType();
  private static final Type LIST_OF_RUN_RECORD = new TypeToken<List<RunRecord>>() { }.getType();

  private static final String WORDCOUNT_APP_NAME = "WordCountApp";
  private static final String WORDCOUNT_FLOW_NAME = "WordCountFlow";
  private static final String WORDCOUNT_MAPREDUCE_NAME = "VoidMapReduceJob";
  private static final String WORDCOUNT_FLOWLET_NAME = "StreamSource";
  private static final String DUMMY_APP_ID = "dummy";
  private static final String DUMMY_MR_NAME = "dummy-batch";
  private static final String SLEEP_WORKFLOW_APP_ID = "SleepWorkflowApp";
  private static final String SLEEP_WORKFLOW_NAME = "SleepWorkflow";
  private static final String APP_WITH_SERVICES_APP_ID = "AppWithServices";
  private static final String APP_WITH_SERVICES_SERVICE_NAME = "NoOpService";
  private static final String APP_WITH_WORKFLOW_APP_ID = "AppWithWorkflow";
  private static final String APP_WITH_WORKFLOW_WORKFLOW_NAME = "SampleWorkflow";
  private static final String APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME = "AppWithMultipleScheduledWorkflows";
  private static final String APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW = "SomeWorkflow";
  private static final String APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW = "AnotherWorkflow";

  private static final String EMPTY_ARRAY_JSON = "[]";
  private static final String STOPPED = "STOPPED";
  private static final String RUNNING = "RUNNING";

  @Category(XSlowTests.class)
  @Test
  public void testProgramStartStopStatus() throws Exception {
    // deploy, check the status
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    Id.Flow wordcountFlow1 = Id.Flow.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME);
    Id.Flow wordcountFlow2 = Id.Flow.from(TEST_NAMESPACE2, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME);

    // flow is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(wordcountFlow1));

    // start flow in the wrong namespace and verify that it does not start
    startProgram(wordcountFlow2, 404);
    Assert.assertEquals(STOPPED, getProgramStatus(wordcountFlow1));

    // start a flow and check the status
    startProgram(wordcountFlow1);
    waitState(wordcountFlow1, ProgramRunStatus.RUNNING.toString());

    // stop the flow and check the status
    stopProgram(wordcountFlow1);
    waitState(wordcountFlow1, STOPPED);

    // deploy another app in a different namespace and verify
    response = deploy(DummyAppWithTrackingTable.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    Id.Program dummyMR1 = Id.Program.from(TEST_NAMESPACE1, DUMMY_APP_ID, ProgramType.MAPREDUCE, DUMMY_MR_NAME);
    Id.Program dummyMR2 = Id.Program.from(TEST_NAMESPACE2, DUMMY_APP_ID, ProgramType.MAPREDUCE, DUMMY_MR_NAME);

    // mapreduce is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(dummyMR2));

    // start mapreduce in the wrong namespace and verify it does not start
    startProgram(dummyMR1, 404);
    Assert.assertEquals(STOPPED, getProgramStatus(dummyMR2));

    // start map-reduce and verify status
    startProgram(dummyMR2);
    waitState(dummyMR2, ProgramRunStatus.RUNNING.toString());

    // stop the mapreduce program and check the status
    stopProgram(dummyMR2);
    waitState(dummyMR2, STOPPED);

    // start multiple runs of the map-reduce program
    startProgram(dummyMR2);
    startProgram(dummyMR2);

    // verify that more than one map-reduce program runs are running
    verifyProgramRuns(dummyMR2, "running", 1);

    // get run records corresponding to the program runs
    List<RunRecord> historyRuns = getProgramRuns(dummyMR2, "running");
    Assert.assertEquals(2, historyRuns.size());

    // stop individual runs of the map-reduce program
    String runId = historyRuns.get(0).getPid();
    stopProgram(dummyMR2, runId, 200);

    runId = historyRuns.get(1).getPid();
    stopProgram(dummyMR2, runId, 200);

    waitState(dummyMR2, STOPPED);

    // start multiple runs of the map-reduce program
    startProgram(dummyMR2);
    startProgram(dummyMR2);
    verifyProgramRuns(dummyMR2, "running", 1);
    historyRuns = getProgramRuns(dummyMR2, "running");
    Assert.assertEquals(2, historyRuns.size());

    // stop all runs of the map-reduce program
    stopProgram(dummyMR2, 200);
    waitState(dummyMR2, STOPPED);

    // get run records, all runs should be stopped
    historyRuns = getProgramRuns(dummyMR2, "running");
    Assert.assertTrue(historyRuns.isEmpty());

    // deploy an app containing a workflow
    response = deploy(SleepingWorkflowApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    Id.Program sleepWorkflow1 =
      Id.Program.from(TEST_NAMESPACE1, SLEEP_WORKFLOW_APP_ID, ProgramType.WORKFLOW, SLEEP_WORKFLOW_NAME);
    Id.Program sleepWorkflow2 =
      Id.Program.from(TEST_NAMESPACE2, SLEEP_WORKFLOW_APP_ID, ProgramType.WORKFLOW, SLEEP_WORKFLOW_NAME);

    // workflow is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(sleepWorkflow2));

    // start workflow in the wrong namespace and verify that it does not start
    startProgram(sleepWorkflow1, 404);
    Assert.assertEquals(STOPPED, getProgramStatus(sleepWorkflow2));

    // start workflow and check status
    startProgram(sleepWorkflow2);
    waitState(sleepWorkflow2, ProgramRunStatus.RUNNING.toString());

    // workflow will stop itself
    waitState(sleepWorkflow2, STOPPED);

    // cleanup
    response = doDelete(getVersionedAPIPath("apps/", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1));
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    response = doDelete(getVersionedAPIPath("apps/", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2));
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testVersionedProgramStartStopStatus() throws Exception {
    Id.Artifact wordCountArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "wordcountapp", VERSION1);
    addAppArtifact(wordCountArtifactId, WordCountApp.class);
    AppRequest<? extends Config> wordCountRequest = new AppRequest<>(
      new ArtifactSummary(wordCountArtifactId.getName(), wordCountArtifactId.getVersion().getVersion()));

    ApplicationId wordCountApp1 = NamespaceId.DEFAULT.app("WordCountApp", VERSION1);
    ProgramId wordcountFlow1 = wordCountApp1.program(ProgramType.FLOW, "WordCountFlow");

    Id.Application wordCountAppDefault = wordCountApp1.toId();
    Id.Program wordcountFlowDefault = wordcountFlow1.toId();

    ApplicationId wordCountApp2 = NamespaceId.DEFAULT.app("WordCountApp", VERSION2);
    ProgramId wordcountFlow2 = wordCountApp2.program(ProgramType.FLOW, "WordCountFlow");

    // Start wordCountApp1
    Assert.assertEquals(200, deploy(wordCountApp1, wordCountRequest).getStatusLine().getStatusCode());

    // Start wordCountApp1 with default version
    Assert.assertEquals(200, deploy(wordCountAppDefault, wordCountRequest).getStatusLine().getStatusCode());

    // flow is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(wordcountFlow1));
    // start flow
    startProgram(wordcountFlow1, 200);
    waitState(wordcountFlow1, RUNNING);
    // same flow cannot be run concurrently in the same app version
    startProgram(wordcountFlow1, 409);

    // start flow in a wrong namespace
    startProgram(new NamespaceId(TEST_NAMESPACE1)
                            .app(wordcountFlow1.getApplication(), wordcountFlow1.getVersion())
                            .program(wordcountFlow1.getType(), wordcountFlow1.getProgram()), 404);

    // Start the second version of the app
    Assert.assertEquals(200, deploy(wordCountApp2, wordCountRequest).getStatusLine().getStatusCode());

    // same flow cannot be run concurrently in multiple versions of the same app
    startProgram(wordcountFlow2, 409);
    startProgram(wordcountFlowDefault, 409);

    stopProgram(wordcountFlow1, null, 200, null);
    waitState(wordcountFlow1, "STOPPED");

    // wordcountFlow2 can be run after wordcountFlow1 is stopped
    startProgram(wordcountFlow2, 200);
    stopProgram(wordcountFlow2, null, 200, null);

    ProgramId wordFrequencyService1 = wordCountApp1.program(ProgramType.SERVICE, "WordFrequencyService");
    ProgramId wordFrequencyService2 = wordCountApp2.program(ProgramType.SERVICE, "WordFrequencyService");
    Id.Program wordFrequencyServiceDefault = wordFrequencyService1.toId();
    // service is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(wordFrequencyService1));
    // start service
    startProgram(wordFrequencyService1, 200);
    waitState(wordFrequencyService1, RUNNING);
    // wordFrequencyService2 is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(wordFrequencyService2));
    // start service in version2
    startProgram(wordFrequencyService2, 200);
    waitState(wordFrequencyService2, RUNNING);
    // wordFrequencyServiceDefault is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(wordFrequencyServiceDefault));
    // start service in default version
    startProgram(wordFrequencyServiceDefault, 200);
    waitState(wordFrequencyServiceDefault, RUNNING);
    // same service cannot be run concurrently in the same app version
    startProgram(wordFrequencyService1, 409);
    stopProgram(wordFrequencyService1, null, 200, null);
    Assert.assertEquals(STOPPED, getProgramStatus(wordFrequencyService1));
    // wordFrequencyService1 can be run after wordFrequencyService1 is stopped
    startProgram(wordFrequencyService1, 200);

    stopProgram(wordFrequencyService1, null, 200, null);
    stopProgram(wordFrequencyService2, null, 200, null);
    stopProgram(wordFrequencyServiceDefault, null, 200, null);

    Id.Artifact sleepWorkflowArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "sleepworkflowapp", VERSION1);
    addAppArtifact(sleepWorkflowArtifactId, SleepingWorkflowApp.class);
    AppRequest<? extends Config> sleepWorkflowRequest = new AppRequest<>(
      new ArtifactSummary(sleepWorkflowArtifactId.getName(), sleepWorkflowArtifactId.getVersion().getVersion()));

    ApplicationId sleepWorkflowApp1 = new ApplicationId(Id.Namespace.DEFAULT.getId(), "SleepingWorkflowApp", VERSION1);
    ProgramId sleepWorkflow1 = sleepWorkflowApp1.program(ProgramType.WORKFLOW, "SleepWorkflow");

    ApplicationId sleepWorkflowApp2 = new ApplicationId(Id.Namespace.DEFAULT.getId(), "SleepingWorkflowApp", VERSION2);
    ProgramId sleepWorkflow2 = sleepWorkflowApp2.program(ProgramType.WORKFLOW, "SleepWorkflow");

    // Start wordCountApp1
    Assert.assertEquals(200, deploy(sleepWorkflowApp1, sleepWorkflowRequest).getStatusLine().getStatusCode());
    // workflow is stopped initially
    Assert.assertEquals(STOPPED, getProgramStatus(sleepWorkflow1));
    // start workflow in a wrong version
    startProgram(sleepWorkflow2, 404);
    // Start wordCountApp2
    Assert.assertEquals(200, deploy(sleepWorkflowApp2, sleepWorkflowRequest).getStatusLine().getStatusCode());

    // start multiple workflow simultaneously
    startProgram(sleepWorkflow1, 200);
    startProgram(sleepWorkflow2, 200);
    startProgram(sleepWorkflow1, 200);
    startProgram(sleepWorkflow2, 200);
    // stop multiple workflow simultaneously
    // This will stop all concurrent runs of the Workflow version 1.0.0
    stopProgram(sleepWorkflow1, null, 200, null);
    // This will stop all concurrent runs of the Workflow version 2.0.0
    stopProgram(sleepWorkflow2, null, 200, null);
    Assert.assertEquals(STOPPED, getProgramStatus(sleepWorkflow1));
    Assert.assertEquals(STOPPED, getProgramStatus(sleepWorkflow2));

    //Test for runtime args
    testVersionedProgramRuntimeArgs(sleepWorkflow1);

    // cleanup
    deleteApp(wordCountApp1, 200);
    deleteApp(wordCountApp2, 200);
    deleteApp(wordCountAppDefault, 200);
    deleteApp(sleepWorkflowApp1, 200);
    deleteApp(sleepWorkflowApp2, 200);
  }

  @Category(XSlowTests.class)
  @Test
  public void testProgramStartStopStatusErrors() throws Exception {
    // deploy, check the status
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // start unknown program
    startProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, "noexist"), 404);
    // start program in unknonw app
    startProgram(Id.Program.from(TEST_NAMESPACE1, "noexist", ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);
    // start program in unknown namespace
    startProgram(Id.Program.from("noexist", WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);

    // debug unknown program
    debugProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, "noexist"), 404);
    // debug a program that does not support it
    debugProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.MAPREDUCE, WORDCOUNT_MAPREDUCE_NAME),
                 501); // not implemented

    // status for unknown program
    programStatus(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, "noexist"), 404);
    // status for program in unknonw app
    programStatus(Id.Program.from(TEST_NAMESPACE1, "noexist", ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);
    // status for program in unknown namespace
    programStatus(Id.Program.from("noexist", WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);

    // stop unknown program
    stopProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, "noexist"), 404);
    // stop program in unknonw app
    stopProgram(Id.Program.from(TEST_NAMESPACE1, "noexist", ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);
    // stop program in unknown namespace
    stopProgram(Id.Program.from("noexist", WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 404);
    // stop program that is not running
    stopProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 400);
    // stop run of a program with ill-formed run id
    stopProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME),
                "norunid", 400);

    // start program twice
    startProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME));
    waitState(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), RUNNING);

    startProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME),
                 409); // conflict

    // get run records for later use
    List<RunRecord> runs = getProgramRuns(
      Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), "running");
    Assert.assertEquals(1, runs.size());
    String runId = runs.get(0).getPid();

    // stop program
    stopProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), 200);
    waitState(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME), "STOPPED");

    // get run records again, should be empty now
    Tasks.waitFor(true, new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Id.Program id = Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME);
        return getProgramRuns(id, "running").isEmpty();
      }
    }, 10, TimeUnit.SECONDS);

    // stop run of the program that is not running
    stopProgram(Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME),
                runId, 404); // active run not found

    // cleanup
    response = doDelete(getVersionedAPIPath("apps/", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1));
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
  }
    /**
     * Tests history of a flow.
     */
  @Test
  public void testFlowHistory() throws Exception {
    testHistory(WordCountApp.class,
                Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME));
  }

  /**
   * Tests history of a mapreduce.
   */
  @Category(XSlowTests.class)
  @Test
  public void testMapreduceHistory() throws Exception {
    testHistory(DummyAppWithTrackingTable.class,
                Id.Program.from(TEST_NAMESPACE2, DUMMY_APP_ID, ProgramType.MAPREDUCE, DUMMY_MR_NAME));
  }

  /**
   * Tests history of a non existing program
   */
  @Test
  public void testNonExistingProgramHistory() throws Exception {
    ProgramId program = new ProgramId(TEST_NAMESPACE2, DUMMY_APP_ID, ProgramType.MAPREDUCE, DUMMY_MR_NAME);
    deploy(DummyAppWithTrackingTable.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    int historyStatus = doPost(getVersionedAPIPath("apps/" + DUMMY_APP_ID + ProgramType.MAPREDUCE + "/NonExisting",
                                                   Constants.Gateway.API_VERSION_3_TOKEN,
                                                   TEST_NAMESPACE2)).getStatusLine().getStatusCode();
    int deleteStatus = doDelete(getVersionedAPIPath("apps/" + DUMMY_APP_ID, Constants.Gateway.API_VERSION_3_TOKEN,
                                                    TEST_NAMESPACE2)).getStatusLine().getStatusCode();
    Assert.assertTrue("Unexpected history status " + historyStatus + " and/or deleteStatus " + deleteStatus,
                      historyStatus == 404 && deleteStatus == 200);
  }

  /**
   * Tests getting a non-existent namespace
   */
  @Test
  public void testNonExistentNamespace() throws Exception {
    String[] endpoints = {"flows", "spark", "services", "workers", "mapreduce", "workflows"};

    for (String endpoint : endpoints) {
      HttpResponse response = doGet("/v3/namespaces/default/" + endpoint);
      Assert.assertEquals(200, response.getStatusLine().getStatusCode());
      response = doGet("/v3/namespaces/garbage/" + endpoint);
      Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }
  }

  /**
   * Tests history of a workflow.
   */
  @Category(SlowTests.class)
  @Test
  public void testWorkflowHistory() throws Exception {
    try {
      deploy(SleepingWorkflowApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
      Id.Program sleepWorkflow1 =
        Id.Program.from(TEST_NAMESPACE1, SLEEP_WORKFLOW_APP_ID, ProgramType.WORKFLOW, SLEEP_WORKFLOW_NAME);

      // first run
      startProgram(sleepWorkflow1);
      waitState(sleepWorkflow1, ProgramRunStatus.RUNNING.toString());
      // workflow stops by itself after actions are done
      waitState(sleepWorkflow1, STOPPED);

      // second run
      startProgram(sleepWorkflow1);
      waitState(sleepWorkflow1, ProgramRunStatus.RUNNING.toString());
      // workflow stops by itself after actions are done
      waitState(sleepWorkflow1, STOPPED);

      String url = String.format("apps/%s/%s/%s/runs?status=completed", SLEEP_WORKFLOW_APP_ID, ProgramType.WORKFLOW
        .getCategoryName(), SLEEP_WORKFLOW_NAME);
      historyStatusWithRetry(getVersionedAPIPath(url, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1), 2);

    } finally {
      Assert.assertEquals(200, doDelete(getVersionedAPIPath("apps/" + SLEEP_WORKFLOW_APP_ID, Constants.Gateway
        .API_VERSION_3_TOKEN, TEST_NAMESPACE1)).getStatusLine().getStatusCode());
    }
  }

  @Test
  public void testFlowRuntimeArgs() throws Exception {
    testRuntimeArgs(WordCountApp.class, TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW.getCategoryName(),
                    WORDCOUNT_FLOW_NAME);
  }

  @Test
  public void testWorkflowRuntimeArgs() throws Exception {
    testRuntimeArgs(SleepingWorkflowApp.class, TEST_NAMESPACE2, SLEEP_WORKFLOW_APP_ID, ProgramType.WORKFLOW
      .getCategoryName(), SLEEP_WORKFLOW_NAME);
  }

  @Test
  public void testMapreduceRuntimeArgs() throws Exception {
    testRuntimeArgs(DummyAppWithTrackingTable.class, TEST_NAMESPACE1, DUMMY_APP_ID, ProgramType.MAPREDUCE
      .getCategoryName(), DUMMY_MR_NAME);
  }

  @Test
  public void testBatchStatus() throws Exception {
    final String statusUrl1 = getVersionedAPIPath("status", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    final String statusUrl2 = getVersionedAPIPath("status", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);

    // invalid json must return 400
    Assert.assertEquals(400, doPost(statusUrl1, "").getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(statusUrl2, "").getStatusLine().getStatusCode());
    // empty array is valid args
    Assert.assertEquals(200, doPost(statusUrl1, EMPTY_ARRAY_JSON).getStatusLine().getStatusCode());
    Assert.assertEquals(200, doPost(statusUrl2, EMPTY_ARRAY_JSON).getStatusLine().getStatusCode());

    // deploy an app in namespace1
    deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    // deploy another app in namespace2
    deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);

    // data requires appId, programId, and programType. Test missing fields/invalid programType
    Assert.assertEquals(400, doPost(statusUrl1, "[{'appId':'WordCountApp', 'programType':'Flow'}]")
      .getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(statusUrl1, "[{'appId':'WordCountApp', 'programId':'WordCountFlow'}]")
      .getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(statusUrl1, "[{'programType':'Flow', 'programId':'WordCountFlow'}, {'appId':" +
      "'AppWithServices', 'programType': 'service', 'programId': 'NoOpService'}]").getStatusLine().getStatusCode());
    Assert.assertEquals(400,
                        doPost(statusUrl1, "[{'appId':'WordCountApp', 'programType':'Flow' " +
                          "'programId':'WordCountFlow'}]").getStatusLine().getStatusCode());
    // Test missing app, programType, etc
    List<JsonObject> returnedBody = readResponse(doPost(statusUrl1, "[{'appId':'NotExist', 'programType':'Flow', " +
      "'programId':'WordCountFlow'}]"), LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(new NotFoundException(new ApplicationId("testnamespace1", "NotExist")).getMessage(),
                        returnedBody.get(0).get("error").getAsString());
    returnedBody = readResponse(
      doPost(statusUrl1, "[{'appId':'WordCountApp', 'programType':'flow', 'programId':'NotExist'}," +
        "{'appId':'WordCountApp', 'programType':'flow', 'programId':'WordCountFlow'}]"), LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(new NotFoundException(new ProgramId("testnamespace1", "WordCountApp", ProgramType.FLOW,
                                                            "NotExist")).getMessage(),
                        returnedBody.get(0).get("error").getAsString());
    Assert.assertEquals(
      new NotFoundException(
        new ProgramId("testnamespace1", "WordCountApp", ProgramType.FLOW, "NotExist")).getMessage(),
      returnedBody.get(0).get("error").getAsString());
    // The programType should be consistent. Second object should have proper status
    Assert.assertEquals("Flow", returnedBody.get(1).get("programType").getAsString());
    Assert.assertEquals(STOPPED, returnedBody.get(1).get("status").getAsString());


    // test valid cases for namespace1
    HttpResponse response = doPost(statusUrl1,
                                   "[{'appId':'WordCountApp', 'programType':'Flow', 'programId':'WordCountFlow'}," +
                                     "{'appId': 'WordCountApp', 'programType': 'Service', 'programId': " +
                                     "'WordFrequencyService'}]");
    verifyInitialBatchStatusOutput(response);

    // test valid cases for namespace2
    response = doPost(statusUrl2, "[{'appId': 'AppWithServices', 'programType': 'Service', 'programId': " +
      "'NoOpService'}]");
    verifyInitialBatchStatusOutput(response);


    // start the flow
    Id.Program wordcountFlow1 =
      Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME);
    Id.Program service2 = Id.Program.from(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID,
                                          ProgramType.SERVICE, APP_WITH_SERVICES_SERVICE_NAME);
    startProgram(wordcountFlow1);

    // test status API after starting the flow
    response = doPost(statusUrl1, "[{'appId':'WordCountApp', 'programType':'Flow', 'programId':'WordCountFlow'}," +
      "{'appId': 'WordCountApp', 'programType': 'Mapreduce', 'programId': 'VoidMapReduceJob'}]");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(ProgramRunStatus.RUNNING.toString(), returnedBody.get(0).get("status").getAsString());
    Assert.assertEquals(STOPPED, returnedBody.get(1).get("status").getAsString());

    // start the service
    startProgram(service2);
    // test status API after starting the service
    response = doPost(statusUrl2, "[{'appId': 'AppWithServices', 'programType': 'Service', 'programId': " +
      "'NoOpService'}]");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(ProgramRunStatus.RUNNING.toString(), returnedBody.get(0).get("status").getAsString());

    // stop the flow
    stopProgram(wordcountFlow1);
    waitState(wordcountFlow1, STOPPED);

    // stop the service
    stopProgram(service2);
    waitState(service2, STOPPED);

    // try posting a status request with namespace2 for apps in namespace1
    response = doPost(statusUrl2, "[{'appId':'WordCountApp', 'programType':'Flow', 'programId':'WordCountFlow'}," +
      "{'appId': 'WordCountApp', 'programType': 'Service', 'programId': 'WordFrequencyService'}," +
      "{'appId': 'WordCountApp', 'programType': 'Mapreduce', 'programId': 'VoidMapReduceJob'}]");
    returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(new NotFoundException(new ApplicationId("testnamespace2", "WordCountApp")).getMessage(),
                        returnedBody.get(0).get("error").getAsString());
    Assert.assertEquals(new NotFoundException(new ApplicationId("testnamespace2", "WordCountApp")).getMessage(),
                        returnedBody.get(1).get("error").getAsString());
    Assert.assertEquals(new NotFoundException(new ApplicationId("testnamespace2", "WordCountApp")).getMessage(),
                        returnedBody.get(2).get("error").getAsString());
  }

  @Test
  public void testBatchInstances() throws Exception {
    final String instancesUrl1 = getVersionedAPIPath("instances", Constants.Gateway.API_VERSION_3_TOKEN,
                                                     TEST_NAMESPACE1);
    final String instancesUrl2 = getVersionedAPIPath("instances", Constants.Gateway.API_VERSION_3_TOKEN,
                                                     TEST_NAMESPACE2);

    Assert.assertEquals(400, doPost(instancesUrl1, "").getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(instancesUrl2, "").getStatusLine().getStatusCode());

    // empty array is valid args
    Assert.assertEquals(200, doPost(instancesUrl1, "[]").getStatusLine().getStatusCode());
    Assert.assertEquals(200, doPost(instancesUrl2, "[]").getStatusLine().getStatusCode());

    deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);

    // data requires appId, programId, and programType. Test missing fields/invalid programType
    // TODO: These json strings should be replaced with JsonObjects so it becomes easier to refactor in future
    Assert.assertEquals(400, doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programType':'Flow'}]")
      .getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programId':'WordCountFlow'}]")
      .getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(instancesUrl1, "[{'programType':'Flow', 'programId':'WordCountFlow'}," +
      "{'appId': 'WordCountApp', 'programType': 'Mapreduce', 'programId': 'WordFrequency'}]")
      .getStatusLine().getStatusCode());
    Assert.assertEquals(400, doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programType':'NotExist', " +
      "'programId':'WordCountFlow'}]").getStatusLine().getStatusCode());

    // Test malformed json
    Assert.assertEquals(400,
                        doPost(instancesUrl1,
                               "[{'appId':'WordCountApp', 'programType':'Flow' 'programId':'WordCountFlow'}]")
                          .getStatusLine().getStatusCode());

    // Test missing app, programType, etc
    List<JsonObject> returnedBody = readResponse(
      doPost(instancesUrl1, "[{'appId':'NotExist', 'programType':'Flow', 'programId':'WordCountFlow'}]"),
      LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(404, returnedBody.get(0).get("statusCode").getAsInt());
    returnedBody = readResponse(
      doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programType':'flow', 'programId':'WordCountFlow', " +
        "'runnableId': " +
        "NotExist'}]"), LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(404, returnedBody.get(0).get("statusCode").getAsInt());


    // valid test in namespace1
    HttpResponse response = doPost(instancesUrl1,
                                   "[{'appId':'WordCountApp', 'programType':'Flow', 'programId':'WordCountFlow', " +
                                     "'runnableId': 'StreamSource'}," +
                                     "{'appId': 'WordCountApp', 'programType': 'Service', 'programId': " +
                                     "'WordFrequencyService', 'runnableId': 'WordFrequencyService'}]");

    verifyInitialBatchInstanceOutput(response);

    // valid test in namespace2
    response = doPost(instancesUrl2,
                      "[{'appId': 'AppWithServices', 'programType':'Service', 'programId':'NoOpService', " +
                        "'runnableId':'NoOpService'}]");
    verifyInitialBatchInstanceOutput(response);


    // start the flow
    Id.Program wordcountFlow1 =
      Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME);
    startProgram(wordcountFlow1);

    response = doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programType':'Flow', 'programId':'WordCountFlow'," +
      "'runnableId': 'StreamSource'}]");
    returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(1, returnedBody.get(0).get("provisioned").getAsInt());

    // start the service
    Id.Program service2 = Id.Program.from(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID,
                                          ProgramType.SERVICE, APP_WITH_SERVICES_SERVICE_NAME);
    startProgram(service2);

    response = doPost(instancesUrl2, "[{'appId':'AppWithServices', 'programType':'Service','programId':'NoOpService'," +
      " 'runnableId':'NoOpService'}]");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    Assert.assertEquals(1, returnedBody.get(0).get("provisioned").getAsInt());

    // request for 2 more instances of the flowlet
    Assert.assertEquals(200, requestFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME,
                                                     WORDCOUNT_FLOWLET_NAME, 2));
    returnedBody = readResponse(doPost(instancesUrl1, "[{'appId':'WordCountApp', 'programType':'Flow'," +
      "'programId':'WordCountFlow', 'runnableId': 'StreamSource'}]"), LIST_OF_JSONOBJECT_TYPE);
    // verify that 2 more instances were requested
    Assert.assertEquals(2, returnedBody.get(0).get("requested").getAsInt());


    stopProgram(wordcountFlow1);
    stopProgram(service2);
    waitState(wordcountFlow1, STOPPED);
    waitState(service2, STOPPED);
  }

  /**
   * Tests for program list calls
   */
  @Test
  public void testProgramList() throws Exception {
    // test initial state
    testListInitialState(TEST_NAMESPACE1, ProgramType.FLOW);
    testListInitialState(TEST_NAMESPACE2, ProgramType.MAPREDUCE);
    testListInitialState(TEST_NAMESPACE1, ProgramType.WORKFLOW);
    testListInitialState(TEST_NAMESPACE2, ProgramType.SPARK);
    testListInitialState(TEST_NAMESPACE1, ProgramType.SERVICE);

    // deploy WordCountApp in namespace1 and verify
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // deploy AppWithServices in namespace2 and verify
    response = deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // verify list by namespace
    verifyProgramList(TEST_NAMESPACE1, ProgramType.FLOW, 1);
    verifyProgramList(TEST_NAMESPACE1, ProgramType.MAPREDUCE, 1);
    verifyProgramList(TEST_NAMESPACE2, ProgramType.SERVICE, 1);

    // verify list by app
    verifyProgramList(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, 1);
    verifyProgramList(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.MAPREDUCE, 1);
    verifyProgramList(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.WORKFLOW, 0);
    verifyProgramList(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID, ProgramType.SERVICE, 1);

    // verify invalid namespace
    Assert.assertEquals(404, getAppFDetailResponseCode(TEST_NAMESPACE1, APP_WITH_SERVICES_APP_ID,
                                                       ProgramType.SERVICE.getCategoryName()));
    // verify invalid app
    Assert.assertEquals(404, getAppFDetailResponseCode(TEST_NAMESPACE1, "random", ProgramType.FLOW.getCategoryName()));
  }

  /**
   * Worker Specification tests
   */
  @Test
  public void testWorkerSpecification() throws Exception {
    // deploy AppWithWorker in namespace1 and verify
    HttpResponse response = deploy(AppWithWorker.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    verifyProgramSpecification(TEST_NAMESPACE1, AppWithWorker.NAME, ProgramType.WORKER.getCategoryName(),
                               AppWithWorker.WORKER);
    Assert.assertEquals(404, getProgramSpecificationResponseCode(TEST_NAMESPACE2, AppWithWorker.NAME,
                                                                 ProgramType.WORKER.getCategoryName(),
                                                                 AppWithWorker.WORKER));
  }

  @Test
  public void testServiceSpecification() throws Exception {
    deploy(AppWithServices.class);
    HttpResponse response = doGet("/v3/namespaces/default/apps/AppWithServices/services/NoOpService");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    Set<ServiceHttpEndpoint> expectedEndpoints = ImmutableSet.of(new ServiceHttpEndpoint("GET", "/ping"),
                                                                 new ServiceHttpEndpoint("POST", "/multi"),
                                                                 new ServiceHttpEndpoint("GET", "/multi"),
                                                                 new ServiceHttpEndpoint("GET", "/multi/ping"));

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(ServiceSpecification.class, new ServiceSpecificationCodec());
    Gson gson = gsonBuilder.create();
    ServiceSpecification specification = readResponse(response, ServiceSpecification.class, gson);

    Set<ServiceHttpEndpoint> returnedEndpoints = new HashSet<>();
    for (HttpServiceHandlerSpecification httpServiceHandlerSpecification : specification.getHandlers().values()) {
      returnedEndpoints.addAll(httpServiceHandlerSpecification.getEndpoints());
    }

    Assert.assertEquals("NoOpService", specification.getName());
    Assert.assertTrue(returnedEndpoints.equals(expectedEndpoints));
  }

  /**
   * Program specification tests through appfabric apis.
   */
  @Test
  public void testProgramSpecification() throws Exception {
    // deploy WordCountApp in namespace1 and verify
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // deploy AppWithServices in namespace2 and verify
    response = deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // deploy AppWithWorkflow in namespace2 and verify
    response = deploy(AppWithWorkflow.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // deploy AppWithWorker in namespace1 and verify
    response = deploy(AppWithWorker.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // verify program specification
    verifyProgramSpecification(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW.getCategoryName(),
                               WORDCOUNT_FLOW_NAME);
    verifyProgramSpecification(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.MAPREDUCE.getCategoryName(),
                               WORDCOUNT_MAPREDUCE_NAME);
    verifyProgramSpecification(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID, ProgramType.SERVICE.getCategoryName(),
                               APP_WITH_SERVICES_SERVICE_NAME);
    verifyProgramSpecification(TEST_NAMESPACE2, APP_WITH_WORKFLOW_APP_ID, ProgramType.WORKFLOW.getCategoryName(),
                               APP_WITH_WORKFLOW_WORKFLOW_NAME);
    verifyProgramSpecification(TEST_NAMESPACE1, AppWithWorker.NAME, ProgramType.WORKER.getCategoryName(),
                               AppWithWorker.WORKER);

    // verify invalid namespace
    Assert.assertEquals(404, getProgramSpecificationResponseCode(TEST_NAMESPACE1, APP_WITH_SERVICES_APP_ID,
                                                                 ProgramType.SERVICE.getCategoryName(),
                                                                 APP_WITH_SERVICES_SERVICE_NAME));
    // verify invalid app
    Assert.assertEquals(404, getProgramSpecificationResponseCode(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID,
                                                                 ProgramType.WORKFLOW.getCategoryName(),
                                                                 APP_WITH_WORKFLOW_WORKFLOW_NAME));
    // verify invalid program type
    Assert.assertEquals(405, getProgramSpecificationResponseCode(TEST_NAMESPACE2, APP_WITH_SERVICES_APP_ID,
                                                                 "random", APP_WITH_WORKFLOW_WORKFLOW_NAME));

    // verify invalid program type
    Assert.assertEquals(404, getProgramSpecificationResponseCode(TEST_NAMESPACE2, AppWithWorker.NAME,
                                                                 ProgramType.WORKER.getCategoryName(),
                                                                 AppWithWorker.WORKER));
  }

  @Test
  public void testFlows() throws Exception {
    // deploy WordCountApp in namespace1 and verify
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // check initial flowlet instances
    int initial = getFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME, WORDCOUNT_FLOWLET_NAME);
    Assert.assertEquals(1, initial);

    // request two more instances
    Assert.assertEquals(200, requestFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME,
                                                     WORDCOUNT_FLOWLET_NAME, 3));
    // verify
    int after = getFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME, WORDCOUNT_FLOWLET_NAME);
    Assert.assertEquals(3, after);

    // invalid namespace
    Assert.assertEquals(404, requestFlowletInstances(TEST_NAMESPACE2, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME,
                                                     WORDCOUNT_FLOWLET_NAME, 3));
    // invalid app
    Assert.assertEquals(404, requestFlowletInstances(TEST_NAMESPACE1, APP_WITH_SERVICES_APP_ID, WORDCOUNT_FLOW_NAME,
                                                     WORDCOUNT_FLOWLET_NAME, 3));
    // invalid flow
    Assert.assertEquals(404, requestFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, "random",
                                                     WORDCOUNT_FLOWLET_NAME, 3));
    // invalid flowlet
    Assert.assertEquals(404, requestFlowletInstances(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME,
                                                     "random", 3));

    // test live info
    // send invalid program type to live info
    response = sendLiveInfoRequest(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, "random", WORDCOUNT_FLOW_NAME);
    Assert.assertEquals(405, response.getStatusLine().getStatusCode());

    // test valid live info
    JsonObject liveInfo = getLiveInfo(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW.getCategoryName(),
                                      WORDCOUNT_FLOW_NAME);
    Assert.assertEquals(WORDCOUNT_APP_NAME, liveInfo.get("app").getAsString());
    Assert.assertEquals(ProgramType.FLOW.getPrettyName(), liveInfo.get("type").getAsString());
    Assert.assertEquals(WORDCOUNT_FLOW_NAME, liveInfo.get("id").getAsString());

    // start flow
    Id.Program wordcountFlow1 =
      Id.Program.from(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW, WORDCOUNT_FLOW_NAME);
    startProgram(wordcountFlow1);

    liveInfo = getLiveInfo(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, ProgramType.FLOW.getCategoryName(),
                           WORDCOUNT_FLOW_NAME);
    Assert.assertEquals(WORDCOUNT_APP_NAME, liveInfo.get("app").getAsString());
    Assert.assertEquals(ProgramType.FLOW.getPrettyName(), liveInfo.get("type").getAsString());
    Assert.assertEquals(WORDCOUNT_FLOW_NAME, liveInfo.get("id").getAsString());
    Assert.assertEquals("in-memory", liveInfo.get("runtime").getAsString());

    // should not delete queues while running
    Assert.assertEquals(403, deleteQueues(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME));
    Assert.assertEquals(403, deleteQueues(TEST_NAMESPACE1));

    // stop
    stopProgram(wordcountFlow1);

    // delete queues
    Assert.assertEquals(200, deleteQueues(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME));
  }

  @Test
  public void testMultipleWorkflowSchedules() throws Exception {
    // Deploy the app
    NamespaceId testNamespace2 = new NamespaceId(TEST_NAMESPACE2);
    Id.Namespace idTestNamespace2 = testNamespace2.toId();
    Id.Artifact artifactId = Id.Artifact.from(idTestNamespace2, "appwithmultiplescheduledworkflows", VERSION1);
    addAppArtifact(artifactId, AppWithMultipleScheduledWorkflows.class);
    AppRequest<? extends Config> appRequest = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()));
    Id.Application appDefault = new Id.Application(idTestNamespace2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME);
    ApplicationId app1 = testNamespace2.app(APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME, VERSION1);
    ApplicationId app2 = testNamespace2.app(APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME, VERSION2);
    Assert.assertEquals(200, deploy(appDefault, appRequest).getStatusLine().getStatusCode());
    Assert.assertEquals(200, deploy(app1, appRequest).getStatusLine().getStatusCode());
    Assert.assertEquals(200, deploy(app2, appRequest).getStatusLine().getStatusCode());

    // Schedule spec from non-versioned API
    List<ScheduleDetail> someSchedules = getSchedules(TEST_NAMESPACE2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME,
                                                      APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW);
    Assert.assertEquals(2, someSchedules.size());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSchedules.get(0).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSchedules.get(1).getProgram().getProgramName());

    // Schedule spec from non-versioned API
    List<ScheduleDetail> anotherSchedules = getSchedules(TEST_NAMESPACE2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME,
                                                         APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW);
    Assert.assertEquals(3, anotherSchedules.size());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules.get(0).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules.get(1).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules.get(2).getProgram().getProgramName());

    deleteApp(appDefault, 200);

    // Schedule of app1 from versioned API
    List<ScheduleDetail> someSchedules1 = getSchedules(TEST_NAMESPACE2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME,
                                                       VERSION1, APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW);
    Assert.assertEquals(2, someSchedules1.size());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSchedules1.get(0).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSchedules1.get(1).getProgram().getProgramName());
    // validate backward-compatible API
    List<ScheduleSpecification> someSpecs1 = getScheduleSpecs(TEST_NAMESPACE2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME,
                                                              VERSION1, APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW);
    Assert.assertEquals(2, someSpecs1.size());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSpecs1.get(0).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_SOMEWORKFLOW, someSpecs1.get(1).getProgram().getProgramName());

    deleteApp(app1, 200);

    // Schedule spec of app2 from versioned API
    List<ScheduleDetail> anotherSchedules2 = getSchedules(TEST_NAMESPACE2, APP_WITH_MULTIPLE_WORKFLOWS_APP_NAME,
                                                          VERSION2, APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW);
    Assert.assertEquals(3, anotherSchedules2.size());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules2.get(0).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules2.get(1).getProgram().getProgramName());
    Assert.assertEquals(APP_WITH_MULTIPLE_WORKFLOWS_ANOTHERWORKFLOW,
                        anotherSchedules2.get(2).getProgram().getProgramName());

    deleteApp(app2, 200);
  }

  @Test
  public void testServices() throws Exception {
    HttpResponse response = deploy(AppWithServices.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    Id.Service service1 = Id.Service.from(Id.Namespace.from(TEST_NAMESPACE1), APP_WITH_SERVICES_APP_ID,
                                          APP_WITH_SERVICES_SERVICE_NAME);
    final Id.Service service2 = Id.Service.from(Id.Namespace.from(TEST_NAMESPACE2), APP_WITH_SERVICES_APP_ID,
                                                APP_WITH_SERVICES_SERVICE_NAME);
    HttpResponse activeResponse = getServiceAvailability(service1);
    // Service is not valid, so it should return 404
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), activeResponse.getStatusLine().getStatusCode());

    activeResponse = getServiceAvailability(service2);
    // Service has not been started, so it should return 503
    Assert.assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE.getCode(),
                        activeResponse.getStatusLine().getStatusCode());

    // start service in wrong namespace
    startProgram(service1, 404);
    startProgram(service2);

    Tasks.waitFor(200, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return getServiceAvailability(service2).getStatusLine().getStatusCode();
      }
    }, 2, TimeUnit.SECONDS, 10, TimeUnit.MILLISECONDS);

    // verify instances
    try {
      getServiceInstances(service1);
      Assert.fail("Should not find service in " + TEST_NAMESPACE1);
    } catch (AssertionError expected) {
      // expected
    }
    ServiceInstances instances = getServiceInstances(service2);
    Assert.assertEquals(1, instances.getRequested());
    Assert.assertEquals(1, instances.getProvisioned());

    // request 2 additional instances
    int code = setServiceInstances(service1, 3);
    Assert.assertEquals(404, code);
    code = setServiceInstances(service2, 3);
    Assert.assertEquals(200, code);

    // verify that additional instances were provisioned
    instances = getServiceInstances(service2);
    Assert.assertEquals(3, instances.getRequested());
    Assert.assertEquals(3, instances.getProvisioned());

    // verify that endpoints are not available in the wrong namespace
    response = callService(service1, HttpMethod.POST, "multi");
    code = response.getStatusLine().getStatusCode();
    Assert.assertEquals(404, code);

    response = callService(service1, HttpMethod.GET, "multi/ping");
    code = response.getStatusLine().getStatusCode();
    Assert.assertEquals(404, code);

    // stop service
    stopProgram(service1, 404);
    stopProgram(service2);

    activeResponse = getServiceAvailability(service2);
    // Service has been stopped, so it should return 503
    Assert.assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE.getCode(),
                        activeResponse.getStatusLine().getStatusCode());
  }

  @Test
  public void testDeleteQueues() throws Exception {
    QueueName queueName = QueueName.fromFlowlet(TEST_NAMESPACE1, WORDCOUNT_APP_NAME, WORDCOUNT_FLOW_NAME,
                                                WORDCOUNT_FLOWLET_NAME, "out");

    // enqueue some data
    enqueue(queueName, new QueueEntry("x".getBytes(Charsets.UTF_8)));

    // verify it exists
    Assert.assertTrue(dequeueOne(queueName));

    // clear queue in wrong namespace
    Assert.assertEquals(200, doDelete("/v3/namespaces/" + TEST_NAMESPACE2 + "/queues").getStatusLine().getStatusCode());
    // verify queue is still here
    Assert.assertTrue(dequeueOne(queueName));

    // clear queue in the right namespace
    Assert.assertEquals(200, doDelete("/v3/namespaces/" + TEST_NAMESPACE1 + "/queues").getStatusLine().getStatusCode());

    // verify queue is gone
    Assert.assertFalse(dequeueOne(queueName));
  }

  @Test
  public void testSchedules() throws Exception {
    // deploy an app with schedule
    Id.Artifact artifactId = Id.Artifact.from(TEST_NAMESPACE_META1.getNamespaceId().toId(),
                                              AppWithSchedule.NAME, VERSION1);
    addAppArtifact(artifactId, AppWithSchedule.class);
    AppRequest<? extends Config> request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()));
    ApplicationId defaultAppId = TEST_NAMESPACE_META1.getNamespaceId().app(AppWithSchedule.NAME);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    // deploy another version of the app
    ApplicationId appV2Id = TEST_NAMESPACE_META1.getNamespaceId().app(AppWithSchedule.NAME, VERSION2);
    Assert.assertEquals(200, deploy(appV2Id, request).getStatusLine().getStatusCode());

    // list schedules for default version app, for the workflow and for the app, they should be same
    List<ScheduleDetail> schedules =
      getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(1, schedules.size());
    ScheduleDetail schedule = schedules.get(0);
    Assert.assertEquals(SchedulableProgramType.WORKFLOW, schedule.getProgram().getProgramType());
    Assert.assertEquals(AppWithSchedule.WORKFLOW_NAME, schedule.getProgram().getProgramName());
    Assert.assertEquals(new TimeTrigger("0/15 * * * * ?"), schedule.getTrigger());

    // there should be two schedules now
    List<ScheduleDetail> schedulesForApp = listSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, null);
    Assert.assertEquals(1, schedulesForApp.size());
    Assert.assertEquals(schedules, schedulesForApp);
    // validate backward compatible api
    List<ScheduleSpecification> specsForApp = listScheduleSpecs(TEST_NAMESPACE1, AppWithSchedule.NAME, null);
    Assert.assertEquals(1, specsForApp.size());
    ScheduleSpecification spec = specsForApp.get(0);
    Assert.assertEquals(AppWithSchedule.WORKFLOW_NAME, spec.getProgram().getProgramName());
    Assert.assertTrue(spec.getSchedule() instanceof TimeSchedule);
    Assert.assertEquals("0/15 * * * * ?", ((TimeSchedule) spec.getSchedule()).getCronEntry());

    List<ScheduleDetail> schedules2 =
      getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(1, schedules2.size());
    ScheduleDetail schedule2 = schedules2.get(0);
    Assert.assertEquals(SchedulableProgramType.WORKFLOW, schedule2.getProgram().getProgramType());
    Assert.assertEquals(AppWithSchedule.WORKFLOW_NAME, schedule2.getProgram().getProgramName());
    Assert.assertEquals(new TimeTrigger("0/15 * * * * ?"), schedule2.getTrigger());

    String newSchedule = "newTimeSchedule";
    testAddSchedule(newSchedule);
    testDeleteSchedule(appV2Id, newSchedule);
    testUpdateSchedule(appV2Id);
  }

  @Test
  public void testUpdateSchedulesFlag() throws Exception {
    // deploy an app with schedule
    AppWithSchedule.AppConfig config = new AppWithSchedule.AppConfig(true, true, true);

    Id.Artifact artifactId = Id.Artifact.from(TEST_NAMESPACE_META2.getNamespaceId().toId(),
                                              AppWithSchedule.NAME, VERSION1);
    addAppArtifact(artifactId, AppWithSchedule.class);
    AppRequest<? extends Config> request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, false);

    ApplicationId defaultAppId = TEST_NAMESPACE_META2.getNamespaceId().app(AppWithSchedule.NAME);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    List<ScheduleDetail> actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                                         defaultAppId.getApplication(), defaultAppId.getVersion());

    // none of the schedules will be added as we have set update schedules to be false
    Assert.assertEquals(0, actualSchedules.size());

    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, true);

    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                    defaultAppId.getApplication(), defaultAppId.getVersion());
    Assert.assertEquals(2, actualSchedules.size());

    // with workflow, without schedule
    config = new AppWithSchedule.AppConfig(true, false, false);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, false);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    // schedule should not be updated
    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                    defaultAppId.getApplication(),
                                    defaultAppId.getVersion());
    Assert.assertEquals(2, actualSchedules.size());

    // without workflow and schedule, schedule should be deleted
    config = new AppWithSchedule.AppConfig(false, false, false);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, false);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                   defaultAppId.getApplication(),
                                   defaultAppId.getVersion());
    Assert.assertEquals(0, actualSchedules.size());

    // with workflow and  one schedule, schedule should be added
    config = new AppWithSchedule.AppConfig(true, true, false);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, true);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                    defaultAppId.getApplication(),
                                    defaultAppId.getVersion());
    Assert.assertEquals(1, actualSchedules.size());
    Assert.assertEquals("SampleSchedule", actualSchedules.get(0).getName());

    // with workflow and two schedules, but update-schedules is false, so 2nd schedule should not get added
    config = new AppWithSchedule.AppConfig(true, true, true);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, false);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                    defaultAppId.getApplication(),
                                    defaultAppId.getVersion());
    Assert.assertEquals(1, actualSchedules.size());
    Assert.assertEquals("SampleSchedule", actualSchedules.get(0).getName());

    // same config, but update-schedule flag is true now, so 2 schedules should be available now
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, true);
    Assert.assertEquals(200, deploy(defaultAppId, request).getStatusLine().getStatusCode());

    actualSchedules = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                   defaultAppId.getApplication(),
                                   defaultAppId.getVersion());
    Assert.assertEquals(2, actualSchedules.size());
  }

  private void testAddSchedule(String scheduleName) throws Exception {
    TimeSchedule timeSchedule = (TimeSchedule) Schedules.builder(scheduleName)
      .setDescription("Something")
      .createTimeSchedule("0 * * * ?");
    TimeTrigger timeTrigger = new TimeTrigger("0 * * * ?");
    ScheduleProgramInfo programInfo = new ScheduleProgramInfo(SchedulableProgramType.WORKFLOW,
                                                              AppWithSchedule.WORKFLOW_NAME);
    ImmutableMap<String, String> properties = ImmutableMap.of("a", "b", "c", "d");
    ScheduleSpecification specification = new ScheduleSpecification(timeSchedule, programInfo, properties);
    ScheduleDetail detail = new ScheduleDetail(scheduleName, specification.getSchedule().getDescription(),
                                               specification.getProgram(), specification.getProperties(),
                                               new TimeTrigger(timeSchedule.getCronEntry()),
                                               Collections.<Constraint>emptyList(),
                                               Schedulers.JOB_QUEUE_TIMEOUT_MILLIS);

    // trying to add the schedule with different name in path param than schedule spec should fail
    HttpResponse response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, "differentName", detail);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.getCode(), response.getStatusLine().getStatusCode());

    // adding a schedule to a non-existing app should fail
    response = addSchedule(TEST_NAMESPACE1, "nonExistingApp", null, scheduleName, specification);
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // adding a schedule to invalid type of program type should fail
    ScheduleDetail invalidScheduleDetail = new ScheduleDetail(
      scheduleName, "Something", new ScheduleProgramInfo(SchedulableProgramType.MAPREDUCE, AppWithSchedule.MAPREDUCE),
      properties, timeTrigger, ImmutableList.<Constraint>of(), TimeUnit.MINUTES.toMillis(1));
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, scheduleName, invalidScheduleDetail);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.getCode(), response.getStatusLine().getStatusCode());

    // adding a schedule for a program that does not exist
    ScheduleSpecification nonExistingSpecification = new ScheduleSpecification(
      timeSchedule, new ScheduleProgramInfo(SchedulableProgramType.MAPREDUCE, "nope"), properties);
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, scheduleName, nonExistingSpecification);
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // adding a schedule with invalid schedule details should fail
    TimeSchedule invalidTimeSchedule = (TimeSchedule) Schedules.builder("invalidTimeSchedule")
      .setDescription("Something")
      .createTimeSchedule("0 * ? * ?"); // invalid cron expression
    // Intentionally keep this ScheduleSpecification to test backward compatibility
    ScheduleSpecification invalidSpecification =
      new ScheduleSpecification(invalidTimeSchedule, programInfo, properties);
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, "invalidTimeSchedule",
                           invalidSpecification);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.getCode(), response.getStatusLine().getStatusCode());

    // test adding a schedule
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, scheduleName, specification);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());

    List<ScheduleDetail> schedules = getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(2, schedules.size());
    Assert.assertEquals(detail, schedules.get(1));

    List<ScheduleDetail> schedulesForApp = listSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, null);
    Assert.assertEquals(schedules, schedulesForApp);

    // trying to add ScheduleDetail of the same schedule again should fail with AlreadyExistsException
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, scheduleName, detail);
    Assert.assertEquals(HttpResponseStatus.CONFLICT.getCode(), response.getStatusLine().getStatusCode());

    // although we should be able to add schedule to a different version of the app
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, scheduleName, detail);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());

    // this should not have affected the schedules of the default version
    List<ScheduleDetail> scheds = getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(schedules, scheds);

    // there should be two schedules now for version 2
    List<ScheduleDetail> schedules2 =
      getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(2, schedules2.size());
    Assert.assertEquals(detail, schedules2.get(1));

    List<ScheduleDetail> schedulesForApp2 = listSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2);
    Assert.assertEquals(schedules2, schedulesForApp2);

    // Add a schedule with no schedule name in spec
    ScheduleDetail detail2 = new ScheduleDetail(null, "Something 2", programInfo, properties,
                                                new TimeTrigger("0 * * * ?"),
                                                Collections.<Constraint>emptyList(), TimeUnit.HOURS.toMillis(6));
    response = addSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, "schedule-100", detail2);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    ScheduleDetail detail100 = getSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, "schedule-100");
    Assert.assertEquals("schedule-100", detail100.getName());
    Assert.assertEquals(detail2.getTimeoutMillis(), detail100.getTimeoutMillis());
    // test backward-compatible api
    ScheduleSpecification spec100 = getScheduleSpec(TEST_NAMESPACE1, AppWithSchedule.NAME, VERSION2, "schedule-100");
    Assert.assertEquals(detail100.toScheduleSpec(), spec100);
  }

  private void testDeleteSchedule(ApplicationId appV2Id, String scheduleName) throws Exception {
    // trying to delete a schedule from a non-existing app should fail
    HttpResponse response = deleteSchedule(TEST_NAMESPACE1, "nonExistingApp", null, scheduleName);
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // trying to delete a non-existing schedule should fail
    response = deleteSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, "nonExistingSchedule");
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // trying to delete a valid existing schedule should pass
    response = deleteSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, scheduleName);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    List<ScheduleDetail> schedules = getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(1, schedules.size());

    // the above schedule delete should not have affected the schedule with same name in another version of the app
    schedules = getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, appV2Id.getVersion(),
                             AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(3, schedules.size());

    // should have a schedule with the given name
    boolean foundSchedule = false;
    for (ScheduleDetail schedule : schedules) {
      if (schedule.getName().equals(scheduleName)) {
        foundSchedule = true;
      }
    }
    Assert.assertTrue(String.format("Expected to find a schedule named %s but didn't", scheduleName), foundSchedule);

    // delete the schedule from the other version of the app too as a cleanup
    response = deleteSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, appV2Id.getVersion(), scheduleName);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    schedules = getSchedules(TEST_NAMESPACE1, AppWithSchedule.NAME, appV2Id.getVersion(),
                             AppWithSchedule.WORKFLOW_NAME);
    Assert.assertEquals(2, schedules.size());
  }

  private void testUpdateSchedule(ApplicationId appV2Id) throws Exception {
    // intentionally keep two ScheduleUpdateDetail's to test backward compatibility
    ScheduleUpdateDetail scheduleUpdateDetail = new ScheduleUpdateDetail("updatedDescription", new RunConstraints(5),
                                                                         "0 4 * * *", null, null,
                                                                         ImmutableMap.of("twoKey", "twoValue",
                                                                                         "someKey", "newValue"));

    ScheduleUpdateDetail invalidUpdateDetail = new ScheduleUpdateDetail("updatedDescription", null, null, "streamName",
                                                                        null, ImmutableMap.<String, String>of());
    ScheduleDetail validScheduleDetail = new ScheduleDetail(
      AppWithSchedule.SCHEDULE, "updatedDescription", null, ImmutableMap.<String, String>of(),
      new StreamSizeTrigger(new NamespaceId(TEST_NAMESPACE1).stream(AppWithSchedule.STREAM), 10),
      ImmutableList.<Constraint>of(new ConcurrencyConstraint(5)), null);

    // trying to update schedule for a non-existing app should fail
    HttpResponse response = updateSchedule(TEST_NAMESPACE1, "nonExistingApp", null, AppWithSchedule.SCHEDULE,
                                           scheduleUpdateDetail);
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // trying to update a non-existing schedule should fail
    response = updateSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null,
                              "NonExistingSchedule", scheduleUpdateDetail);
    Assert.assertEquals(HttpResponseStatus.NOT_FOUND.getCode(), response.getStatusLine().getStatusCode());

    // trying to update a time schedule with stream schedule detail containing null dataTriggerMB should fail
    response = updateSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE,
                              invalidUpdateDetail);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.getCode(), response.getStatusLine().getStatusCode());

    // trying to update a time schedule with stream schedule detail containing both
    // stream name and dataTriggerMB should succeed
    response = updateSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE,
                              validScheduleDetail);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());

    // should be able to update an existing stream size schedule with a valid new time schedule
    response = updateSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE,
                              scheduleUpdateDetail);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());

    // verify that the schedule information for updated
    ScheduleDetail schedule = getSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE);
    Assert.assertEquals("updatedDescription", schedule.getDescription());
    Assert.assertEquals("0 4 * * *", ((TimeTrigger) schedule.getTrigger()).getCronExpression());
    Assert.assertEquals(new ProtoConstraint.ConcurrencyConstraint(5), schedule.getConstraints().get(0));
    // the properties should have been replaced
    Assert.assertEquals(2, schedule.getProperties().size());
    Assert.assertEquals("newValue", schedule.getProperties().get("someKey"));
    Assert.assertEquals("twoValue", schedule.getProperties().get("twoKey"));
    // the old property should not exist
    Assert.assertNull(schedule.getProperties().get("oneKey"));

    // the above update should not have affected the schedule for the other version of the app
    schedule = getSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, appV2Id.getVersion(), AppWithSchedule.SCHEDULE);
    Assert.assertNotEquals("updatedDescription", schedule.getDescription());
    Assert.assertEquals("0/15 * * * * ?", ((TimeTrigger) schedule.getTrigger()).getCronExpression());

    // try to update the schedule again but this time with property as null. It should retain the old properties
    ScheduleDetail scheduleDetail = new ScheduleDetail(AppWithSchedule.SCHEDULE, "updatedDescription", null, null,
                                                       new TimeTrigger("0 4 * * *"), null, null);
    response = updateSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE, scheduleDetail);
    Assert.assertEquals(HttpResponseStatus.OK.getCode(), response.getStatusLine().getStatusCode());
    schedule = getSchedule(TEST_NAMESPACE1, AppWithSchedule.NAME, null, AppWithSchedule.SCHEDULE);
    Assert.assertEquals(2, schedule.getProperties().size());
    Assert.assertEquals("newValue", schedule.getProperties().get("someKey"));
    Assert.assertEquals("twoValue", schedule.getProperties().get("twoKey"));
    Assert.assertEquals(new ProtoConstraint.ConcurrencyConstraint(5), schedule.getConstraints().get(0));
  }

  @After
  public void cleanup() throws Exception {
    doDelete(getVersionedAPIPath("apps/", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1));
    doDelete(getVersionedAPIPath("apps/", Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE2));
  }

  // TODO: Duplicate from AppFabricHttpHandlerTest, remove the AppFabricHttpHandlerTest method after deprecating v2 APIs
  private  void enqueue(QueueName queueName, final QueueEntry queueEntry) throws Exception {
    QueueClientFactory queueClientFactory = AppFabricTestBase.getInjector().getInstance(QueueClientFactory.class);
    final QueueProducer producer = queueClientFactory.createProducer(queueName);
    // doing inside tx
    TransactionExecutorFactory txExecutorFactory =
      AppFabricTestBase.getInjector().getInstance(TransactionExecutorFactory.class);
    txExecutorFactory.createExecutor(ImmutableList.of((TransactionAware) producer))
      .execute(new TransactionExecutor.Subroutine() {
        @Override
        public void apply() throws Exception {
          // write more than one so that we can dequeue multiple times for multiple checks
          // we only dequeue twice, but ensure that the drop queues call drops the rest of the entries as well
          int numEntries = 0;
          while (numEntries++ < 5) {
            producer.enqueue(queueEntry);
          }
        }
      });
  }

  private boolean dequeueOne(QueueName queueName) throws Exception {
    QueueClientFactory queueClientFactory = AppFabricTestBase.getInjector().getInstance(QueueClientFactory.class);
    final QueueConsumer consumer = queueClientFactory.createConsumer(queueName,
                                                                     new ConsumerConfig(1L, 0, 1,
                                                                                        DequeueStrategy.ROUND_ROBIN,
                                                                                        null),
                                                                     1);
    // doing inside tx
    TransactionExecutorFactory txExecutorFactory =
      AppFabricTestBase.getInjector().getInstance(TransactionExecutorFactory.class);
    return txExecutorFactory.createExecutor(ImmutableList.of((TransactionAware) consumer))
      .execute(new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return !consumer.dequeue(1).isEmpty();
        }
      });
  }

  private HttpResponse getServiceAvailability(Id.Service serviceId) throws Exception {
    String activeUrl = String.format("apps/%s/services/%s/available", serviceId.getApplicationId(), serviceId.getId());
    String versionedActiveUrl = getVersionedAPIPath(activeUrl, Constants.Gateway.API_VERSION_3_TOKEN,
                                                    serviceId.getNamespaceId());
    return doGet(versionedActiveUrl);
  }

  private ServiceInstances getServiceInstances(Id.Service serviceId) throws Exception {
    String instanceUrl = String.format("apps/%s/services/%s/instances", serviceId.getApplicationId(),
                                       serviceId.getId());
    String versionedInstanceUrl = getVersionedAPIPath(instanceUrl, Constants.Gateway.API_VERSION_3_TOKEN,
                                                      serviceId.getNamespaceId());
    HttpResponse response = doGet(versionedInstanceUrl);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    return readResponse(response, ServiceInstances.class);
  }

  private int setServiceInstances(Id.Service serviceId, int instances) throws Exception {
    String instanceUrl = String.format("apps/%s/services/%s/instances", serviceId.getApplicationId(),
                                       serviceId.getId());
    String versionedInstanceUrl = getVersionedAPIPath(instanceUrl, Constants.Gateway.API_VERSION_3_TOKEN,
                                                      serviceId.getNamespaceId());
    String instancesBody = GSON.toJson(new Instances(instances));
    return doPut(versionedInstanceUrl, instancesBody).getStatusLine().getStatusCode();
  }

  private HttpResponse callService(Id.Service serviceId, HttpMethod method, String endpoint) throws Exception {
    String serviceUrl = String.format("apps/%s/service/%s/methods/%s",
                                      serviceId.getApplicationId(), serviceId.getId(), endpoint);
    String versionedServiceUrl = getVersionedAPIPath(serviceUrl, Constants.Gateway.API_VERSION_3_TOKEN,
                                                     serviceId.getNamespaceId());
    if (HttpMethod.GET.equals(method)) {
      return doGet(versionedServiceUrl);
    } else if (HttpMethod.POST.equals(method)) {
      return doPost(versionedServiceUrl);
    }
    throw new IllegalArgumentException("Only GET and POST supported right now.");
  }

  private int deleteQueues(String namespace) throws Exception {
    String versionedDeleteUrl = getVersionedAPIPath("queues", Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    HttpResponse response = doDelete(versionedDeleteUrl);
    return response.getStatusLine().getStatusCode();
  }

  private int deleteQueues(String namespace, String appId, String flow) throws Exception {
    String deleteQueuesUrl = String.format("apps/%s/flows/%s/queues", appId, flow);
    String versionedDeleteUrl = getVersionedAPIPath(deleteQueuesUrl, Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    HttpResponse response = doDelete(versionedDeleteUrl);
    return response.getStatusLine().getStatusCode();
  }

  private JsonObject getLiveInfo(String namespace, String appId, String programType, String programId)
    throws Exception {
    HttpResponse response = sendLiveInfoRequest(namespace, appId, programType, programId);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    return readResponse(response, JsonObject.class);
  }

  private HttpResponse sendLiveInfoRequest(String namespace, String appId, String programType, String programId)
    throws Exception {
    String liveInfoUrl = String.format("apps/%s/%s/%s/live-info", appId, programType, programId);
    String versionedUrl = getVersionedAPIPath(liveInfoUrl, Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    return doGet(versionedUrl);
  }

  private int requestFlowletInstances(String namespace, String appId, String flow, String flowlet, int noRequested)
    throws Exception {
    String flowletInstancesVersionedUrl = getFlowletInstancesVersionedUrl(namespace, appId, flow, flowlet);
    JsonObject instances = new JsonObject();
    instances.addProperty("instances", noRequested);
    String body = GSON.toJson(instances);
    return doPut(flowletInstancesVersionedUrl, body).getStatusLine().getStatusCode();
  }

  private int getFlowletInstances(String namespace, String appId, String flow, String flowlet) throws Exception {
    String flowletInstancesUrl = getFlowletInstancesVersionedUrl(namespace, appId, flow, flowlet);
    String response = readResponse(doGet(flowletInstancesUrl));
    Assert.assertNotNull(response);
    JsonObject instances = GSON.fromJson(response, JsonObject.class);
    Assert.assertTrue(instances.has("instances"));
    return instances.get("instances").getAsInt();
  }

  private String getFlowletInstancesVersionedUrl(String namespace, String appId, String flow, String flowlet) {
    String flowletInstancesUrl = String.format("apps/%s/%s/%s/flowlets/%s/instances", appId,
                                               ProgramType.FLOW.getCategoryName(), flow, flowlet);
    return getVersionedAPIPath(flowletInstancesUrl, Constants.Gateway.API_VERSION_3_TOKEN, namespace);
  }

  private void verifyProgramSpecification(String namespace, String appId, String programType, String programId)
    throws Exception {
    JsonObject programSpec = getProgramSpecification(namespace, appId, programType, programId);
    Assert.assertTrue(programSpec.has("className") && programSpec.has("name") && programSpec.has("description"));
    Assert.assertEquals(programId, programSpec.get("name").getAsString());
  }

  private JsonObject getProgramSpecification(String namespace, String appId, String programType,
                                             String programId) throws Exception {
    HttpResponse response = requestProgramSpecification(namespace, appId, programType, programId);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    String result = EntityUtils.toString(response.getEntity());
    Assert.assertNotNull(result);
    return GSON.fromJson(result, JsonObject.class);
  }

  private int getProgramSpecificationResponseCode(String namespace, String appId, String programType, String programId)
    throws Exception {
    HttpResponse response = requestProgramSpecification(namespace, appId, programType, programId);
    return response.getStatusLine().getStatusCode();
  }

  private HttpResponse requestProgramSpecification(String namespace, String appId, String programType,
                                                   String programId) throws Exception {
    String uri = getVersionedAPIPath(String.format("apps/%s/%s/%s", appId, programType, programId),
                                     Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    return doGet(uri);
  }

  private void testListInitialState(String namespace, ProgramType programType) throws Exception {
    HttpResponse response = doGet(getVersionedAPIPath(programType.getCategoryName(),
                                                      Constants.Gateway.API_VERSION_3_TOKEN, namespace));
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    Assert.assertEquals(EMPTY_ARRAY_JSON, readResponse(response));
  }

  private void verifyProgramList(String namespace, ProgramType programType, int expected) throws Exception {
    HttpResponse response = requestProgramList(namespace, programType.getCategoryName());
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    String json = EntityUtils.toString(response.getEntity());
    List<Map<String, String>> programs = GSON.fromJson(json, LIST_MAP_STRING_STRING_TYPE);
    Assert.assertEquals(expected, programs.size());
  }

  private void verifyProgramList(String namespace, String appName,
                                 final ProgramType programType, int expected) throws Exception {
    HttpResponse response = requestAppDetail(namespace, appName);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    String json = EntityUtils.toString(response.getEntity());
    ApplicationDetail appDetail = GSON.fromJson(json, ApplicationDetail.class);
    Collection<ProgramRecord> programs = Collections2.filter(appDetail.getPrograms(), new Predicate<ProgramRecord>() {
      @Override
      public boolean apply(@Nullable ProgramRecord record) {
        return programType.getCategoryName().equals(record.getType().getCategoryName());
      }
    });
    Assert.assertEquals(expected, programs.size());
  }

  private int getAppFDetailResponseCode(String namespace, @Nullable String appName, String programType)
    throws Exception {
    HttpResponse response = requestAppDetail(namespace, appName);
    return response.getStatusLine().getStatusCode();
  }

  private HttpResponse requestProgramList(String namespace, String programType)
    throws Exception {
    return doGet(getVersionedAPIPath(programType, Constants.Gateway.API_VERSION_3_TOKEN, namespace));
  }

  private HttpResponse requestAppDetail(String namespace, String appName)
    throws Exception {
    String uri = getVersionedAPIPath(String.format("apps/%s", appName),
                                     Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    return doGet(uri);
  }

  private void verifyInitialBatchStatusOutput(HttpResponse response) throws IOException {
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    List<JsonObject> returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    for (JsonObject obj : returnedBody) {
      Assert.assertEquals(200, obj.get("statusCode").getAsInt());
      Assert.assertEquals(STOPPED, obj.get("status").getAsString());
    }
  }

  private void verifyInitialBatchInstanceOutput(HttpResponse response) throws IOException {
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    List<JsonObject> returnedBody = readResponse(response, LIST_OF_JSONOBJECT_TYPE);
    for (JsonObject obj : returnedBody) {
      Assert.assertEquals(200, obj.get("statusCode").getAsInt());
      Assert.assertEquals(1, obj.get("requested").getAsInt());
      Assert.assertEquals(0, obj.get("provisioned").getAsInt());
    }
  }

  private void testHistory(Class<?> app, Id.Program program) throws Exception {
    String namespace = program.getNamespaceId();
    try {
      deploy(app, Constants.Gateway.API_VERSION_3_TOKEN, namespace);
      verifyProgramHistory(program.toEntityId());
    } catch (Exception e) {
      LOG.error("Got exception: ", e);
    } finally {
      deleteApp(program.getApplication(), 200);
    }
    ApplicationId appId = new ApplicationId(namespace, program.getApplicationId(), VERSION1);
    ProgramId programId = appId.program(program.getType(), program.getId());
    try {
      Id.Artifact artifactId = Id.Artifact.from(program.getNamespace(), app.getSimpleName(), "1.0.0");
      addAppArtifact(artifactId, app);
      AppRequest<Config> request = new AppRequest<>(
        new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), null);
      Assert.assertEquals(200, deploy(appId, request).getStatusLine().getStatusCode());
      verifyProgramHistory(programId);
    } catch (Exception e) {
      LOG.error("Got exception: ", e);
    } finally {
      deleteApp(appId, 200);
    }
  }

  private void verifyProgramHistory(ProgramId program) throws Exception {
    String namespace = program.getNamespace();
    // first run
    startProgram(program, 200);
    waitState(program, ProgramRunStatus.RUNNING.toString());
    stopProgram(program, null, 200, null);
    waitState(program, STOPPED);

    // second run
    startProgram(program, 200);
    waitState(program, ProgramRunStatus.RUNNING.toString());
    String urlAppVersionPart = ApplicationId.DEFAULT_VERSION.equals(program.getVersion()) ?
      "" : "/versions/" + program.getVersion();
    String url = String.format("apps/%s%s/%s/%s/runs?status=running", program.getApplication(), urlAppVersionPart,
                               program.getType().getCategoryName(), program.getProgram());

    //active size should be 1
    historyStatusWithRetry(getVersionedAPIPath(url, Constants.Gateway.API_VERSION_3_TOKEN, namespace), 1);
    // completed runs size should be 1
    url = String.format("apps/%s%s/%s/%s/runs?status=killed", program.getApplication(), urlAppVersionPart,
                        program.getType().getCategoryName(), program.getProgram());
    historyStatusWithRetry(getVersionedAPIPath(url, Constants.Gateway.API_VERSION_3_TOKEN, namespace), 1);

    stopProgram(program, null, 200, null);
    waitState(program, STOPPED);

    historyStatusWithRetry(getVersionedAPIPath(url, Constants.Gateway.API_VERSION_3_TOKEN, namespace), 2);
  }

  private void historyStatusWithRetry(String url, int size) throws Exception {
    int trials = 0;
    while (trials++ < 5) {
      HttpResponse response = doGet(url);
      List<RunRecord> result = GSON.fromJson(EntityUtils.toString(response.getEntity()), LIST_OF_RUN_RECORD);
      if (result != null && result.size() >= size) {
        for (RunRecord m : result) {
          assertRunRecord(String.format("%s/%s", url.substring(0, url.indexOf("?")), m.getPid()),
                          GSON.fromJson(GSON.toJson(m), RunRecord.class));
        }
        break;
      }
      TimeUnit.SECONDS.sleep(1);
    }
    Assert.assertTrue(trials < 5);
  }

  private void assertRunRecord(String url, RunRecord expectedRunRecord) throws Exception {
    HttpResponse response = doGet(url);
    RunRecord actualRunRecord = GSON.fromJson(EntityUtils.toString(response.getEntity()), RunRecord.class);
    Assert.assertEquals(expectedRunRecord, actualRunRecord);
  }

  private void testVersionedProgramRuntimeArgs(ProgramId programId) throws Exception {
    String versionedRuntimeArgsUrl = getVersionedAPIPath("apps/" + programId.getApplication()
                                                           + "/versions/" + programId.getVersion()
                                                           + "/" + programId.getType().getCategoryName()
                                                           + "/" + programId.getProgram() + "/runtimeargs",
                                                         Constants.Gateway.API_VERSION_3_TOKEN,
                                                         programId.getNamespace());
    verifyRuntimeArgs(versionedRuntimeArgsUrl);
  }

  private void testRuntimeArgs(Class<?> app, String namespace, String appId, String programType, String programId)
    throws Exception {
    deploy(app, Constants.Gateway.API_VERSION_3_TOKEN, namespace);

    String versionedRuntimeArgsUrl = getVersionedAPIPath("apps/" + appId + "/" + programType + "/" + programId +
                                                           "/runtimeargs", Constants.Gateway.API_VERSION_3_TOKEN,
                                                         namespace);
    verifyRuntimeArgs(versionedRuntimeArgsUrl);

    String versionedRuntimeArgsAppVersionUrl = getVersionedAPIPath("apps/" + appId
                                                                     + "/versions/" + ApplicationId.DEFAULT_VERSION
                                                                     + "/" + programType
                                                                     + "/" + programId + "/runtimeargs",
                                                                   Constants.Gateway.API_VERSION_3_TOKEN, namespace);
    verifyRuntimeArgs(versionedRuntimeArgsAppVersionUrl);
  }

  private void verifyRuntimeArgs(String url) throws Exception {
    Map<String, String> args = Maps.newHashMap();
    args.put("Key1", "Val1");
    args.put("Key2", "Val1");
    args.put("Key2", "Val1");

    HttpResponse response;
    String argString = GSON.toJson(args, new TypeToken<Map<String, String>>() { }.getType());

    response = doPut(url, argString);

    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    response = doGet(url);

    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    Map<String, String> argsRead = GSON.fromJson(EntityUtils.toString(response.getEntity()),
                                                 new TypeToken<Map<String, String>>() {
                                                 }.getType());

    Assert.assertEquals(args.size(), argsRead.size());

    for (Map.Entry<String, String> entry : args.entrySet()) {
      Assert.assertEquals(entry.getValue(), argsRead.get(entry.getKey()));
    }

    //test empty runtime args
    response = doPut(url, "");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    response = doGet(url);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    argsRead = GSON.fromJson(EntityUtils.toString(response.getEntity()),
                             new TypeToken<Map<String, String>>() {
                             }.getType());
    Assert.assertEquals(0, argsRead.size());

    //test null runtime args
    response = doPut(url, null);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    response = doGet(url);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    argsRead = GSON.fromJson(EntityUtils.toString(response.getEntity()),
                             new TypeToken<Map<String, String>>() {
                             }.getType());
    Assert.assertEquals(0, argsRead.size());
  }
}
