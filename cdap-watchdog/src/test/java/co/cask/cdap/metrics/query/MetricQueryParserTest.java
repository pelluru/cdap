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
package co.cask.cdap.metrics.query;

import co.cask.cdap.api.dataset.lib.cube.Interpolators;
import co.cask.cdap.api.metrics.MetricDataQuery;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.conf.Constants.Metrics.Tag;
import co.cask.cdap.proto.Id;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class MetricQueryParserTest {

  @Test
  public void testPathStrip() {
    String expected = "/system/apps/app1/flows/flow1/metric?aggregate=true";
    String path = Constants.Gateway.API_VERSION_3 + "/metrics" + expected;
    Assert.assertEquals(expected, MetricQueryParser.stripVersionAndMetricsFromPath(path));
  }

  @Test
  public void testQueryArgs() throws MetricsPathException {
    MetricDataQuery query = MetricQueryParser.parse(URI.create("/system/apps/app1/reads?count=60"));
    Assert.assertEquals(60, query.getLimit());

    query = MetricQueryParser.parse(URI.create("/system/apps/app1/reads?aggregate=true"));
    Assert.assertEquals(Integer.MAX_VALUE, query.getResolution());

    query = MetricQueryParser.parse(URI.create("/system/apps/app1/reads?count=60&start=1&end=61&" +
                                                 "resolution=1s"));
    Assert.assertEquals(1, query.getStartTs());
    Assert.assertEquals(61, query.getEndTs());
    Assert.assertEquals(1, query.getResolution());

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=60&start=1&end=61&resolution=1m"));
    Assert.assertEquals(1, query.getStartTs());
    Assert.assertEquals(61, query.getEndTs());
    Assert.assertEquals(60, query.getResolution());
    Assert.assertNull(query.getInterpolator());

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?start=1&end=61&resolution=60m"));
    Assert.assertEquals(1, query.getStartTs());
    Assert.assertEquals(61, query.getEndTs());
    Assert.assertEquals(3600, query.getResolution());
    Assert.assertNull(query.getInterpolator());

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=60&start=1&end=61&interpolate=step"));
    Assert.assertEquals(1, query.getStartTs());
    Assert.assertEquals(61, query.getEndTs());
    Assert.assertTrue(query.getInterpolator() instanceof Interpolators.Step);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=60&start=1&end=61&interpolate=linear"));
    Assert.assertEquals(1, query.getStartTs());
    Assert.assertEquals(61, query.getEndTs());
    Assert.assertTrue(query.getInterpolator() instanceof Interpolators.Linear);
  }

  @Test
  public void testRelativeTimeArgs() throws MetricsPathException  {
    long now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&end=now-5s"));
    assertTimestamp(now - 5, query.getEndTs());
    assertTimestamp(now - 65, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-65s"));
    assertTimestamp(now - 5, query.getEndTs());
    assertTimestamp(now - 65, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-1m"));
    assertTimestamp(now, query.getEndTs());
    assertTimestamp(now - 60, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-1h"));
    assertTimestamp(now - 3600 + 60, query.getEndTs());
    assertTimestamp(now - 3600, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-1d"));
    assertTimestamp(now - 86400 + 60, query.getEndTs());
    assertTimestamp(now - 86400, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-1m&end=now"));
    assertTimestamp(now, query.getEndTs());
    assertTimestamp(now - 60, query.getStartTs());

    now = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/reads?count=61&start=now-2m%2B20s"));
    assertTimestamp(now - 40, query.getEndTs());
    assertTimestamp(now - 100, query.getStartTs());
  }

  // assuming you got the actual timestamp after the expected, check that they are equal,
  // or that the actual is 1 second before the expected in case we were on a second boundary.
  private void assertTimestamp(long expected, long actual) {
    Assert.assertTrue(actual + " not within 1 second of " + expected, expected == actual || (actual - 1) == expected);
  }

  @Test
  public void testOverview() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(URI.create("/system/reads?aggregate=true"));
    Assert.assertTrue(query.getSliceByTags().isEmpty());
    assertMetricName("system.reads", query);
  }

  @Test
  public void testApps() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(URI.create("/system/apps/app1/reads?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1");
    assertMetricName("system.reads", query);
  }

  @Test
  public void testFlow() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/flowlets/flowlet1/process.bytes?count=60&start=1&end=61"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.bytes", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/some.metric?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1");
    assertMetricName("system.some.metric", query);

    //flow with runId
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/runs/1234/some.metric?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1");
    assertMetricName("system.some.metric", query);
    Assert.assertEquals("1234", query.getSliceByTags().get(Tag.RUN_ID));

    //flowlet with runId
    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/runs/1234/flowlets/flowlet1/some.metric?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.some.metric", query);
    Assert.assertEquals("1234", query.getSliceByTags().get(Tag.RUN_ID));
  }

  @Test(expected = MetricsPathException.class)
  public void testMultipleRunIdInvalidPath() throws MetricsPathException  {
    MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/runs/1234/runs/1235/flowlets/flowlet1/some.metric?summary=true"));
  }

  @Test
  public void testQueues() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/flowlets/flowlet1/queues/queue1/process.bytes.in?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.bytes.in", query);
    Assert.assertEquals("queue1", query.getSliceByTags().get(Tag.FLOWLET_QUEUE));

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/flowlets/flowlet1/queues/queue1/process.bytes.out?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.bytes.out", query);
    Assert.assertEquals("queue1", query.getSliceByTags().get(Tag.FLOWLET_QUEUE));

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/flowlets/flowlet1/queues/queue1/process.events.in?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.events.in", query);
    Assert.assertEquals("queue1", query.getSliceByTags().get(Tag.FLOWLET_QUEUE));

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/flowlets/flowlet1/queues/queue1/process.events.out?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.events.out", query);
    Assert.assertEquals("queue1", query.getSliceByTags().get(Tag.FLOWLET_QUEUE));

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/flows/flow1/runs/run123/flowlets/flowlet1/queues/queue1/" +
                   "process.events.out?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.process.events.out", query);
    Assert.assertEquals("queue1", query.getSliceByTags().get(Tag.FLOWLET_QUEUE));
    Assert.assertEquals("run123", query.getSliceByTags().get(Tag.RUN_ID));
  }

  @Test
  public void testMapReduce() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/mapreduce/mapred1/mappers/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.MAPREDUCE, "mapred1",
               Tag.MR_TASK_TYPE, "m");
    assertMetricName("system.reads", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/mapreduce/mapred1/reducers/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.MAPREDUCE, "mapred1",
               Tag.MR_TASK_TYPE, "r");
    assertMetricName("system.reads", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/mapreduce/mapred1/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.MAPREDUCE, "mapred1");
    assertMetricName("system.reads", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/mapreduce/mapred1/runs/run123/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.MAPREDUCE, "mapred1");
    assertMetricName("system.reads", query);
    Assert.assertEquals("run123", query.getSliceByTags().get(Tag.RUN_ID));

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/mapreduce/mapred1/runs/run123/mappers/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.MAPREDUCE, "mapred1",
               Tag.MR_TASK_TYPE, "m");
    assertMetricName("system.reads", query);
    Assert.assertEquals("run123", query.getSliceByTags().get(Tag.RUN_ID));
  }

  @Test
  public void testUserServices() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/services/serve1/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.SERVICE, "serve1");
    assertMetricName("system.reads", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/services/serve1/handlers/handler1/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.SERVICE, "serve1",
               Tag.HANDLER, "handler1");
    assertMetricName("system.reads", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/services/serve1/runs/runid123/handlers/handler1/reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.SERVICE, "serve1",
               Tag.HANDLER, "handler1");
    assertMetricName("system.reads", query);
    Assert.assertEquals("runid123", query.getSliceByTags().get(Tag.RUN_ID));
  }

  @Test
  public void testSpark() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/spark/fakespark/sparkmetric?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.SPARK, "fakespark");
    assertMetricName("system.sparkmetric", query);

    query = MetricQueryParser.parse(
      URI.create("/system/apps/app1/spark/fakespark/runs/runid123/sparkmetric?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.SPARK, "fakespark");
    assertMetricName("system.sparkmetric", query);
    Assert.assertEquals("runid123", query.getSliceByTags().get(Tag.RUN_ID));
  }


  @Test(expected = MetricsPathException.class)
  public void testInvalidUserServices() throws MetricsPathException  {
    MetricQueryParser.parse(URI.create("/system/apps/app1/service/serve1/reads?summary=true"));
  }

  @Test(expected = MetricsPathException.class)
  public void testInvalidUserServicesTooManyPath() throws MetricsPathException  {
    MetricQueryParser.parse(URI.create("/system/apps/app1/services/serve1/runnables/run1/random/reads?summary=true"));
  }

  @Test
  public void testDataset() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/datasets/dataset1/apps/app1/flows/flow1/runs/run1/" +
                   "flowlets/flowlet1/store.reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1",
               Tag.FLOWLET, "flowlet1");
    assertMetricName("system.store.reads", query);
    Assert.assertEquals("dataset1", query.getSliceByTags().get(Tag.DATASET));
    Assert.assertEquals("run1", query.getSliceByTags().get(Tag.RUN_ID));

    query = MetricQueryParser.parse(
      URI.create("/system/datasets/dataset1/apps/app1/flows/flow1/store.reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1");
    assertMetricName("system.store.reads", query);
    Assert.assertEquals("dataset1", query.getSliceByTags().get(Tag.DATASET));

    query = MetricQueryParser.parse(
      URI.create("/system/datasets/dataset1/apps/app1/flows/flow1/runs/123/store.reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1",
               Tag.FLOW, "flow1");
    assertMetricName("system.store.reads", query);
    Assert.assertEquals("dataset1", query.getSliceByTags().get(Tag.DATASET));
    Assert.assertEquals("123", query.getSliceByTags().get(Tag.RUN_ID));

    query = MetricQueryParser.parse(
      URI.create("/system/datasets/dataset1/apps/app1/store.reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1");
    assertMetricName("system.store.reads", query);
    Assert.assertEquals("dataset1", query.getSliceByTags().get(Tag.DATASET));

    query = MetricQueryParser.parse(
      URI.create("/system/datasets/dataset1/store.reads?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId());
    assertMetricName("system.store.reads", query);
    Assert.assertEquals("dataset1", query.getSliceByTags().get(Tag.DATASET));
  }

  @Test
  public void testStream() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/streams/stream1/collect.events?summary=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId());
    assertMetricName("system.collect.events", query);
    Assert.assertEquals("stream1", query.getSliceByTags().get(Tag.STREAM));
  }


  @Test
  public void testService() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/services/appfabric/query.received?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               "system",
               Tag.COMPONENT, "appfabric");
    assertMetricName("system.query.received", query);
  }


  @Test
  public void testHandler() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/services/appfabric/handlers/AppFabricHttpHandler/runs/123/" +
                   "response.server-error?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               "system",
               Tag.COMPONENT, "appfabric",
               Tag.HANDLER, "AppFabricHttpHandler");
    assertMetricName("system.response.server-error", query);
    Assert.assertEquals("123", query.getSliceByTags().get(Tag.RUN_ID));
  }

  @Test
  public void testMethod() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/services/metrics/handlers/MetricsQueryHandler/methods/handleComponent/" +
                   "response.successful?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               "system",
               Tag.COMPONENT, "metrics",
               Tag.HANDLER, "MetricsQueryHandler",
               Tag.METHOD, "handleComponent");
    assertMetricName("system.response.successful", query);
  }

  @Test(expected = MetricsPathException.class)
  public void testInvalidRequest() throws MetricsPathException {
    //handler instead of handlers
    MetricQueryParser.parse(
      URI.create("/system/services/metrics/handler/MetricsQueryHandler/response.successful?aggregate=true"));
  }

  @Test
  public void testCluster() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/cluster/resources.total.storage?count=1&start=12345678&interpolate=step"));
    verifyTags(query.getSliceByTags(), "system");
    assertMetricName("system.resources.total.storage", query);
  }


  @Test
  public void testTransactions() throws MetricsPathException  {
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/system/transactions/invalid?count=1&start=12345678&interpolate=step"));
    verifyTags(query.getSliceByTags(),
               "system",
               Tag.COMPONENT, "transactions");
    assertMetricName("system.invalid", query);
  }

  @Test
  public void testMetricURIDecoding() throws UnsupportedEncodingException, MetricsPathException {
    String weirdMetric = "/weird?me+tr ic#$name////";
    // encoded version or weirdMetric
    String encodedWeirdMetric = "%2Fweird%3Fme%2Btr%20ic%23%24name%2F%2F%2F%2F";
    MetricDataQuery query = MetricQueryParser.parse(
      URI.create("/user/apps/app1/flows/" + encodedWeirdMetric + "?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1");
    assertMetricName("user." + weirdMetric, query);

    query = MetricQueryParser.parse(
      URI.create("/user/apps/app1/" + encodedWeirdMetric + "?aggregate=true"));
    verifyTags(query.getSliceByTags(),
               Id.Namespace.DEFAULT.getId(),
               Tag.APP, "app1");
    assertMetricName("user." + weirdMetric, query);
  }


  @Test(expected = IllegalArgumentException.class)
  public void testUserMetricBadURIThrowsException() throws MetricsPathException {
    String badEncoding = "/%2";
    MetricQueryParser.parse(URI.create("/user/apps/app1/flows" + badEncoding + "?aggregate=true"));
  }

  @Test
  public void testBadPathsThrowExceptions() {
    int numBad = 0;
    String[] validPaths = {
      "/system/metric?aggregate=true",
      "/system/apps/appX/metric?aggregate=true",
      "/system/apps/appX/flows/metric?aggregate=true",
      "/system/apps/appX/flows/flowY/metric?aggregate=true",
      "/system/apps/appX/flows/flowY/flowlets/flowletZ/metric?aggregate=true",
      "/system/apps/appX/mapreduce/metric?aggregate=true",
      "/system/apps/appX/mapreduce/mapreduceY/metric?aggregate=true",
      "/system/apps/appX/mapreduce/mapreduceY/mappers/metric?aggregate=true",
      "/system/apps/appX/mapreduce/mapreduceY/reducers/metric?aggregate=true",
      "/system/datasets/datasetA/metric?aggregate=true",
      "/system/datasets/datasetA/apps/appX/metric?aggregate=true",
      "/system/datasets/datasetA/apps/appX/flows/flowY/metric?aggregate=true",
      "/system/datasets/datasetA/apps/appX/flows/flowY/flowlets/flowletZ/metric?aggregate=true",
      "/system/streams/streamA/metric?aggregate=true"
    };
    // check that miss-spelled paths and the like throw an exception.
    String[] invalidPaths = {
      "/syste/metric?aggregate=true",
      "/system/app/appX/metric?aggregate=true",
      "/system/apps/appX/flow/metric?aggregate=true",
      "/system/apps/appX/flows/flowY/flowlet/flowletZ/metric?aggregate=true",
      "/system/apps/appX/mapreduces/metric?aggregate=true",
      "/system/apps/appX/mapreduces/mapreduceY/metric?aggregate=true",
      "/system/apps/appX/mapreduce/mapreduceY/mapper/metric?aggregate=true",
      "/system/apps/appX/mapreduce/mapreduceY/reducer/metric?aggregate=true",
      "/system/dataset/datasetA/metric?aggregate=true",
      "/system/datasets/datasetA/app/appX/metric?aggregate=true",
      "/system/datasets/datasetA/apps/appX/flow/flowY/metric?aggregate=true",
      "/system/datasets/datasetA/apps/appX/flows/flowY/flowlet/flowletZ/metric?aggregate=true",
      "/system/stream/streamA/metric?aggregate=true"
    };
    for (String path : validPaths) {
      try {
        MetricQueryParser.parse(URI.create(path));
      } catch (MetricsPathException e) {
        numBad++;
      }
    }
    Assert.assertEquals(0, numBad);
    for (String path : invalidPaths) {
      try {
        MetricQueryParser.parse(URI.create(path));
      } catch (MetricsPathException e) {
        numBad++;
      }
    }
    Assert.assertEquals(invalidPaths.length, numBad);
  }

  private void assertMetricName(String expected, MetricDataQuery query) {
    Assert.assertEquals(1, query.getMetrics().size());
    Assert.assertEquals(expected, query.getMetrics().keySet().iterator().next());
  }

  private static void verifyTags(Map<String, String> sliceByTags, String... context) {
    // first is namespace
    String namespace = context[0];
    Assert.assertEquals(namespace, sliceByTags.get(Tag.NAMESPACE));

    for (int i = 1; i < context.length; i += 2) {
      Assert.assertEquals(context[i + 1], sliceByTags.get(context[i]));
    }
  }
}
