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

package co.cask.cdap.client;

import co.cask.cdap.StandaloneTester;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.client.app.FakeApp;
import co.cask.cdap.client.app.FakeFlow;
import co.cask.cdap.client.config.ConnectionConfig;
import co.cask.cdap.explore.client.ExploreClient;
import co.cask.cdap.explore.client.ExploreExecutionResult;
import co.cask.cdap.explore.client.FixedAddressExploreClient;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.QueryResult;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.FlowId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.test.SingletonExternalResource;
import co.cask.cdap.test.XSlowTests;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Test for {@link QueryClient}.
 */
@Category(XSlowTests.class)
public class QueryClientTest extends AbstractClientTest {

  @ClassRule
  public static final SingletonExternalResource STANDALONE = new SingletonExternalResource(new StandaloneTester());

  private static final Logger LOG = LoggerFactory.getLogger(QueryClientTest.class);

  private ApplicationClient appClient;
  private QueryClient queryClient;
  private NamespaceClient namespaceClient;
  private ProgramClient programClient;
  private StreamClient streamClient;
  private ExploreClient exploreClient;

  @Override
  protected StandaloneTester getStandaloneTester() {
    return STANDALONE.get();
  }

  @Before
  public void setUp() throws Throwable {
    super.setUp();

    appClient = new ApplicationClient(clientConfig);
    queryClient = new QueryClient(clientConfig);
    programClient = new ProgramClient(clientConfig);
    streamClient = new StreamClient(clientConfig);
    String accessToken = (clientConfig.getAccessToken() == null) ? null : clientConfig.getAccessToken().getValue();
    ConnectionConfig connectionConfig = clientConfig.getConnectionConfig();
    exploreClient = new FixedAddressExploreClient(connectionConfig.getHostname(), connectionConfig.getPort(),
                                                  accessToken, connectionConfig.isSSLEnabled(),
                                                  clientConfig.isVerifySSLCert());
    namespaceClient = new NamespaceClient(clientConfig);
  }

  @Test
  public void testAll() throws Exception {
    NamespaceId namespace = new NamespaceId("queryClientTestNamespace");
    NamespaceId otherNamespace = new NamespaceId("queryClientOtherNamespace");
    namespaceClient.create(new NamespaceMeta.Builder().setName(namespace).build());

    ApplicationId app = namespace.app(FakeApp.NAME);
    FlowId flow = app.flow(FakeFlow.NAME);
    DatasetId dataset = namespace.dataset(FakeApp.DS_NAME);

    appClient.deploy(namespace, createAppJarFile(FakeApp.class));

    try {
      programClient.start(flow);
      assertProgramRunning(programClient, flow);

      StreamId stream = namespace.stream(FakeApp.STREAM_NAME);
      streamClient.sendEvent(stream, "bob:123");
      streamClient.sendEvent(stream, "joe:321");

      Thread.sleep(3000);

      executeBasicQuery(namespace, FakeApp.DS_NAME);

      exploreClient.disableExploreDataset(dataset).get();
      try {
        queryClient.execute(namespace, "select * from " + FakeApp.DS_NAME).get();
        Assert.fail("Explore Query should have thrown an ExecutionException since explore is disabled");
      } catch (ExecutionException e) {
        // ignored
      }

      exploreClient.enableExploreDataset(dataset).get();
      executeBasicQuery(namespace, FakeApp.DS_NAME);

      try {
        queryClient.execute(otherNamespace, "show tables").get();
        Assert.fail("Explore Query should have thrown an ExecutionException since the database should not exist");
      } catch (ExecutionException e) {
        // expected
      }
    } finally {
      programClient.stop(flow);
      assertProgramStopped(programClient, flow);

      try {
        appClient.delete(app);
      } catch (Exception e) {
        LOG.error("Error deleting app {} during test cleanup.", e);
      }
    }
  }

  private void executeBasicQuery(NamespaceId namespace, String instanceName) throws Exception {
    // Hive replaces the periods with underscores
    String query = "select * from dataset_" + instanceName.replace(".", "_");
    ExploreExecutionResult executionResult = queryClient.execute(namespace, query).get();
    Assert.assertNotNull(executionResult.getResultSchema());
    List<QueryResult> results = Lists.newArrayList(executionResult);
    Assert.assertNotNull(results);
    Assert.assertEquals(2, results.size());

    Assert.assertEquals("bob", Bytes.toString((byte[]) results.get(0).getColumns().get(0)));
    Assert.assertEquals("123", Bytes.toString((byte[]) results.get(0).getColumns().get(1)));
    Assert.assertEquals("joe", Bytes.toString((byte[]) results.get(1).getColumns().get(0)));
    Assert.assertEquals("321", Bytes.toString((byte[]) results.get(1).getColumns().get(1)));
  }
}
