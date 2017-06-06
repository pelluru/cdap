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

package co.cask.cdap.gateway.router;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.conf.SConfiguration;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.internal.guice.AppFabricTestModule;
import co.cask.cdap.route.store.RouteStore;
import co.cask.cdap.security.auth.AccessTokenTransformer;
import co.cask.cdap.security.guice.SecurityModules;
import com.google.common.collect.Maps;
import com.google.common.net.InetAddresses;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.net.DefaultSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.twill.discovery.DiscoveryService;
import org.apache.twill.discovery.DiscoveryServiceClient;

import java.util.Map;
import javax.net.SocketFactory;

/**
 * Tests Netty Router running on HTTP.
 */
public class NettyRouterHttpTest extends NettyRouterTestBase {

  @Override
  protected int lookupService(String serviceName) {
    return super.lookupService(serviceName);
  }

  @Override
  protected RouterService createRouterService() {
    return new HttpRouterService(HOSTNAME, DISCOVERY_SERVICE);
  }

  @Override
  protected String getProtocol() {
    return "http";
  }

  @Override
  protected DefaultHttpClient getHTTPClient() throws Exception {
    return new DefaultHttpClient();
  }

  @Override
  protected SocketFactory getSocketFactory() throws Exception {
    return new DefaultSocketFactory();
  }

  private static class HttpRouterService extends RouterService {
    private final String hostname;
    private final DiscoveryService discoveryService;
    private final Map<String, Integer> serviceMap = Maps.newHashMap();

    private NettyRouter router;

    private HttpRouterService(String hostname, DiscoveryService discoveryService) {
      this.hostname = hostname;
      this.discoveryService = discoveryService;
    }

    @Override
    protected void startUp() {
      CConfiguration cConf = CConfiguration.create();
      SConfiguration sConfiguration = SConfiguration.create();
      Injector injector = Guice.createInjector(new SecurityModules().getInMemoryModules(),
                                               new DiscoveryRuntimeModule().getInMemoryModules(),
                                               new AppFabricTestModule(cConf));
      DiscoveryServiceClient discoveryServiceClient = injector.getInstance(DiscoveryServiceClient.class);
      AccessTokenTransformer accessTokenTransformer = injector.getInstance(AccessTokenTransformer.class);
      RouteStore routeStore = injector.getInstance(RouteStore.class);
      cConf.set(Constants.Router.ADDRESS, hostname);
      cConf.setInt(Constants.Router.ROUTER_PORT, 0);
      cConf.setInt(Constants.Router.CONNECTION_TIMEOUT_SECS, CONNECTION_IDLE_TIMEOUT_SECS);
      router =
        new NettyRouter(cConf, sConfiguration, InetAddresses.forString(hostname),
                        new RouterServiceLookup(cConf, (DiscoveryServiceClient) discoveryService,
                                                new RouterPathLookup(), routeStore),
                        new SuccessTokenValidator(), accessTokenTransformer, discoveryServiceClient);
      router.startAndWait();

      for (Map.Entry<Integer, String> entry : router.getServiceLookup().getServiceMap().entrySet()) {
        serviceMap.put(entry.getValue(), entry.getKey());
      }
    }

    @Override
    protected void shutDown() {
      router.stopAndWait();
    }

    @Override
    public int lookupService(String serviceName) {
      return serviceMap.get(serviceName);
    }
  }

}
