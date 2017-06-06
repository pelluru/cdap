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

package co.cask.cdap.client;

import co.cask.cdap.api.app.Application;
import co.cask.cdap.client.app.AllProgramsApp;
import co.cask.cdap.client.common.ClientTestBase;
import co.cask.cdap.client.config.ConnectionConfig;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.gateway.handlers.UsageHandler;
import co.cask.cdap.proto.ProgramStatus;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.EntityIdCompatible;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.test.XSlowTests;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link co.cask.cdap.gateway.handlers.UsageHandler}
 */
@Category(XSlowTests.class)
public class UsageHandlerTestRun extends ClientTestBase {

  private static final Gson GSON = new Gson();

  // Function to transform an Id to EntityId
  private static final Function<EntityIdCompatible, EntityId>
    ENTITY_ID_TRANSFORMER = new Function<EntityIdCompatible, EntityId>() {
    @Override
    public EntityId apply(EntityIdCompatible id) {
      return id.toEntityId();
    }
  };

  private void deployApp(Class<? extends Application> appCls) throws Exception {
    new ApplicationClient(getClientConfig()).deploy(NamespaceId.DEFAULT, createAppJarFile(appCls));
  }

  private void deleteApp(ApplicationId appId) throws Exception {
    new ApplicationClient(getClientConfig()).delete(appId);
  }

  private void startProgram(ProgramId programId) throws Exception {
    new ProgramClient(getClientConfig()).start(programId);
  }

  private void waitState(final ProgramId programId, ProgramStatus status) throws Exception {
    final ProgramClient programclient = new ProgramClient(getClientConfig());
    Tasks.waitFor(status, new Callable<ProgramStatus>() {
      @Override
      public ProgramStatus call() throws Exception {
        return ProgramStatus.valueOf(programclient.getStatus(programId));
      }
    }, 60, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testFlowUsage() throws Exception {
    final ApplicationId app = NamespaceId.DEFAULT.app(AllProgramsApp.NAME);
    final ProgramId program = app.flow(AllProgramsApp.NoOpFlow.NAME);
    final StreamId stream = NamespaceId.DEFAULT.stream(AllProgramsApp.STREAM_NAME);
    final DatasetId dataset = NamespaceId.DEFAULT.dataset(AllProgramsApp.DATASET_NAME);

    Assert.assertEquals(0, getAppStreamUsage(app).size());
    Assert.assertEquals(0, getProgramStreamUsage(program).size());
    Assert.assertEquals(0, getStreamProgramUsage(stream).size());

    Assert.assertEquals(0, getProgramDatasetUsage(program).size());
    Assert.assertEquals(0, getAppDatasetUsage(app).size());
    Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());

    deployApp(AllProgramsApp.class);

    try {
      Assert.assertTrue(getAppStreamUsage(app).contains(stream));
      Assert.assertTrue(getProgramStreamUsage(program).contains(stream));
      Assert.assertTrue(getStreamProgramUsage(stream).contains(program));

      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppStreamUsage(app).size());
      Assert.assertEquals(0, getProgramStreamUsage(program).size());
      Assert.assertEquals(0, getStreamProgramUsage(stream).size());
    }
  }

  @Test
  public void testWorkerUsage() throws Exception {
    final ApplicationId app = NamespaceId.DEFAULT.app(AllProgramsApp.NAME);
    final ProgramId program = app.worker(AllProgramsApp.NoOpWorker.NAME);
    final StreamId stream = NamespaceId.DEFAULT.stream(AllProgramsApp.STREAM_NAME);
    final DatasetId dataset = NamespaceId.DEFAULT.dataset(AllProgramsApp.DATASET_NAME);

    Assert.assertEquals(0, getAppStreamUsage(app).size());
    Assert.assertEquals(0, getProgramStreamUsage(program).size());
    Assert.assertEquals(0, getStreamProgramUsage(stream).size());

    Assert.assertEquals(0, getAppDatasetUsage(app).size());
    Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());

    deployApp(AllProgramsApp.class);

    try {
      startProgram(program);
      // Wait for the worker to run and then stop.
      waitState(program, ProgramStatus.RUNNING);
      waitState(program, ProgramStatus.STOPPED);

      Assert.assertTrue(getAppStreamUsage(app).contains(stream));
      Assert.assertTrue(getProgramStreamUsage(program).contains(stream));
      Assert.assertTrue(getStreamProgramUsage(stream).contains(program));

      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppStreamUsage(app).size());
      Assert.assertEquals(0, getProgramStreamUsage(program).size());
      Assert.assertEquals(0, getStreamProgramUsage(stream).size());

      Assert.assertEquals(0, getAppDatasetUsage(app).size());
      Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());
    }
  }

  @Test
  public void testMapReduceUsage() throws Exception {
    final ApplicationId app = NamespaceId.DEFAULT.app(AllProgramsApp.NAME);
    final ProgramId program = app.mr(AllProgramsApp.NoOpMR.NAME);
    final StreamId stream = NamespaceId.DEFAULT.stream(AllProgramsApp.STREAM_NAME);
    final DatasetId dataset = NamespaceId.DEFAULT.dataset(AllProgramsApp.DATASET_NAME);

    Assert.assertEquals(0, getAppStreamUsage(app).size());
    Assert.assertEquals(0, getProgramStreamUsage(program).size());
    Assert.assertEquals(0, getStreamProgramUsage(stream).size());

    Assert.assertEquals(0, getAppDatasetUsage(app).size());
    Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());

    deployApp(AllProgramsApp.class);
    // now that we only support dynamic dataset instantiation in initialize (and not in configure as before),
    // we must run the mapreduce program to register its usage
    startProgram(program);
    waitState(program, ProgramStatus.STOPPED);

    try {
      Assert.assertTrue(getAppStreamUsage(app).contains(stream));
      Assert.assertTrue(getProgramStreamUsage(program).contains(stream));
      Assert.assertTrue(getStreamProgramUsage(stream).contains(program));

      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppStreamUsage(app).size());
      Assert.assertEquals(0, getProgramStreamUsage(program).size());
      Assert.assertEquals(0, getStreamProgramUsage(stream).size());

      Assert.assertEquals(0, getAppDatasetUsage(app).size());
      Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());
    }
  }

  @Test
  public void testSparkUsage() throws Exception {
    final ApplicationId app = NamespaceId.DEFAULT.app(AllProgramsApp.NAME);
    final ProgramId program = app.spark(AllProgramsApp.NoOpSpark.NAME);
    final StreamId stream = NamespaceId.DEFAULT.stream(AllProgramsApp.STREAM_NAME);
    final DatasetId dataset = NamespaceId.DEFAULT.dataset(AllProgramsApp.DATASET_NAME);

    Assert.assertEquals(0, getAppStreamUsage(app).size());
    Assert.assertEquals(0, getProgramStreamUsage(program).size());
    Assert.assertEquals(0, getStreamProgramUsage(stream).size());

    Assert.assertEquals(0, getAppDatasetUsage(app).size());
    Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());

    deployApp(AllProgramsApp.class);

    try {
      // the program will run and stop by itself.
      startProgram(program);
      waitState(program, ProgramStatus.STOPPED);

      Assert.assertTrue(getAppStreamUsage(app).contains(stream));
      Assert.assertTrue(getProgramStreamUsage(program).contains(stream));
      Assert.assertTrue(getStreamProgramUsage(stream).contains(program));

      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppStreamUsage(app).size());
      Assert.assertEquals(0, getProgramStreamUsage(program).size());
      Assert.assertEquals(0, getStreamProgramUsage(stream).size());

      Assert.assertEquals(0, getAppDatasetUsage(app).size());
      Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());
    }

    deployApp(AllProgramsApp.class);

    try {
      // the program will run and stop by itself.
      startProgram(program);
      waitState(program, ProgramStatus.STOPPED);

      Assert.assertTrue(getAppStreamUsage(app).contains(stream));
      Assert.assertTrue(getProgramStreamUsage(program).contains(stream));
      Assert.assertTrue(getStreamProgramUsage(stream).contains(program));

      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppStreamUsage(app).size());
      Assert.assertEquals(0, getProgramStreamUsage(program).size());
      Assert.assertEquals(0, getStreamProgramUsage(stream).size());

      Assert.assertEquals(0, getAppDatasetUsage(app).size());
      Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());
    }
  }

  @Test
  public void testServiceUsage() throws Exception {
    final ApplicationId app = NamespaceId.DEFAULT.app(AllProgramsApp.NAME);
    final ProgramId program = app.service(AllProgramsApp.NoOpService.NAME);
    final DatasetId dataset = NamespaceId.DEFAULT.dataset(AllProgramsApp.DATASET_NAME);

    Assert.assertEquals(0, getAppDatasetUsage(app).size());
    Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());

    deployApp(AllProgramsApp.class);

    try {
      Assert.assertTrue(getProgramDatasetUsage(program).contains(dataset));
      Assert.assertTrue(getAppDatasetUsage(app).contains(dataset));
      Assert.assertTrue(getDatasetProgramUsage(dataset).contains(program));
    } finally {
      deleteApp(app);

      Assert.assertEquals(0, getAppDatasetUsage(app).size());
      Assert.assertEquals(0, getDatasetProgramUsage(dataset).size());
    }
  }

  private <T> T doGet(String path, Type responseType) throws IOException {
    ConnectionConfig connectionConfig = getClientConfig().getConnectionConfig();
    URL url = new URL(String.format("http://%s:%d%s",
                                    connectionConfig.getHostname(),
                                    connectionConfig.getPort(), path));

    HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
    try {
      Assert.assertEquals(200, urlConn.getResponseCode());
      try (Reader reader = new BufferedReader(new InputStreamReader(urlConn.getInputStream(), Charsets.UTF_8))) {
        return GSON.fromJson(reader, responseType);
      }
    } finally {
      urlConn.disconnect();
    }
  }

  private Set<EntityId> getAppDatasetUsage(ApplicationId app) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdDatasetInstance> datasetIds =
      doGet(String.format("/v3/namespaces/%s/apps/%s/datasets", app.getNamespace(), app.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdDatasetInstance>>() { }.getType());
    return new HashSet<>(Collections2.transform(datasetIds, ENTITY_ID_TRANSFORMER));
  }

  private Set<EntityId> getAppStreamUsage(ApplicationId app) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdStream> streamIds =
      doGet(String.format("/v3/namespaces/%s/apps/%s/streams", app.getNamespace(), app.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdStream>>() { }.getType());
    return new HashSet<>(Collections2.transform(streamIds, ENTITY_ID_TRANSFORMER));
  }

  private Set<EntityId> getProgramDatasetUsage(ProgramId program) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdDatasetInstance> datasetIds =
      doGet(String.format("/v3/namespaces/%s/apps/%s/%s/%s/datasets",
                          program.getNamespace(), program.getApplication(), program.getType().getCategoryName(),
                          program.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdDatasetInstance>>() { }.getType());
    return new HashSet<>(Collections2.transform(datasetIds, ENTITY_ID_TRANSFORMER));
  }

  private Set<EntityId> getProgramStreamUsage(ProgramId program) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdStream> streamIds =
      doGet(String.format("/v3/namespaces/%s/apps/%s/%s/%s/streams",
                          program.getNamespace(), program.getApplication(), program.getType().getCategoryName(),
                          program.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdStream>>() { }.getType());
    return new HashSet<>(Collections2.transform(streamIds, ENTITY_ID_TRANSFORMER));
  }

  // dataset/stream -> program

  private Set<EntityId> getStreamProgramUsage(StreamId stream) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdProgram> programIds =
      doGet(String.format("/v3/namespaces/%s/streams/%s/programs", stream.getNamespace(), stream.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdProgram>>() { }.getType());
    return new HashSet<>(Collections2.transform(programIds, ENTITY_ID_TRANSFORMER));
  }

  private Set<EntityId> getDatasetProgramUsage(DatasetId dataset) throws Exception {
    Set<UsageHandler.BackwardCompatibility.IdProgram> programIds =
      doGet(String.format("/v3/namespaces/%s/data/datasets/%s/programs",
                          dataset.getNamespace(), dataset.getEntityName()),
            new TypeToken<Set<UsageHandler.BackwardCompatibility.IdProgram>>() { }.getType());
    return new HashSet<>(Collections2.transform(programIds, ENTITY_ID_TRANSFORMER));
  }
}
