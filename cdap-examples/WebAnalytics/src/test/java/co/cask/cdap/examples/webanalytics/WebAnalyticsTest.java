/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.examples.webanalytics;

import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.FlowManager;
import co.cask.cdap.test.StreamManager;
import co.cask.cdap.test.TestBase;
import co.cask.cdap.test.TestConfiguration;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * WebAnalyticsTests essentially just asserts that WebAnalytics App can accept a
 * stream of Apache log lines and insert them into a dataset. However this test
 * does not assert the data has indeed been inserted.
 */
public class WebAnalyticsTest extends TestBase {

  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration(Constants.Explore.EXPLORE_ENABLED, false);

  @Test
  public void testWebAnalytics() throws Exception {
    // Deploy the Application
    ApplicationManager appManager = deployApplication(WebAnalytics.class);
    // Start the Flow
    FlowManager flowManager = appManager.getFlowManager("WebAnalyticsFlow").start();

    // Send events to the Stream
    StreamManager streamManager = getStreamManager("log");
    BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/access.log"),
                                                                     "UTF-8"));
    int lines = 0;
    try {
      String line = reader.readLine();
      while (line != null) {
        streamManager.send(line);
        lines++;
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }

    // Wait for the flow to process all data
    RuntimeMetrics flowletMetrics = flowManager.getFlowletMetrics("UniqueVisitor");
    flowletMetrics.waitForProcessed(lines, 10, TimeUnit.SECONDS);

    // Verify the unique count
    UniqueVisitCount uniqueVisitCount = this.<UniqueVisitCount>getDataset("UniqueVisitCount").get();
    Assert.assertEquals(3L, uniqueVisitCount.getCount("192.168.12.72"));
  }
}
