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

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.gateway.GatewayFastTestsSuite;
import co.cask.cdap.gateway.GatewayTestBase;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test ping handler.
 */
public class PingHandlerTestRun extends GatewayTestBase {
  @Test
  public void testPing() throws Exception {
    HttpResponse response = GatewayFastTestsSuite.doGet("/ping");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    Assert.assertEquals("OK.\n", EntityUtils.toString(response.getEntity()));
  }

  @Test
  public void testStatus() throws Exception {
    HttpResponse response = GatewayFastTestsSuite.doGet("/status");
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    Assert.assertEquals(Constants.Monitor.STATUS_OK, EntityUtils.toString(response.getEntity()));
  }
}
