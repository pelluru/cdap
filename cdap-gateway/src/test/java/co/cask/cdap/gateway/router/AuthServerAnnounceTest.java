/*
 *
 * Copyright © 2016-2017 Cask Data, Inc.
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
import co.cask.cdap.internal.guava.reflect.TypeToken;
import co.cask.cdap.internal.guice.AppFabricTestModule;
import co.cask.cdap.route.store.RouteStore;
import co.cask.cdap.security.auth.AccessTokenTransformer;
import co.cask.cdap.security.guice.SecurityModules;
import co.cask.cdap.security.server.GrantAccessToken;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.twill.discovery.DiscoveryService;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.apache.twill.discovery.InMemoryDiscoveryService;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AuthServerAnnounceTest {
  private static final String HOSTNAME = "127.0.0.1";
  private static final int CONNECTION_IDLE_TIMEOUT_SECS = 2;
  private static final DiscoveryService DISCOVERY_SERVICE = new InMemoryDiscoveryService();
  private static final String ANNOUNCE_URLS = "https://vip.cask.co:80,http://vip.cask.co:1000";
  private static final String ANNOUNCE_ADDRESS_WITH_PORT = "vip1.cask.co:10000";
  private static final String ANNOUNCE_ADDRESS_WITHOUT_PORT = "vip2.cask.co";
  private static final Gson GSON = new Gson();
  private static final Type TYPE = new TypeToken<Map<String, List<String>>>() {
  }.getType();

  @Test
  public void testEmptyAnnounceAddressURLsConfig() throws Exception {
    HttpRouterService routerService = new AuthServerAnnounceTest.HttpRouterService(HOSTNAME, DISCOVERY_SERVICE);
    routerService.startUp();
    try {
      Assert.assertEquals(Collections.EMPTY_LIST, getAuthURI(routerService));
    } finally {
      routerService.shutDown();
    }
  }

  @Test
  public void testAnnounceAddressWithPortConfig() throws Exception {
    HttpRouterService routerService = new AuthServerAnnounceTest.HttpRouterService(HOSTNAME, DISCOVERY_SERVICE);
    routerService.cConf.set(Constants.Security.AUTH_SERVER_ANNOUNCE_ADDRESS_DEPRECATED, ANNOUNCE_ADDRESS_WITH_PORT);
    routerService.startUp();
    try {
      Assert.assertEquals(
        Collections.singletonList(String.format("http://%s/%s", ANNOUNCE_ADDRESS_WITH_PORT,
                                                GrantAccessToken.Paths.GET_TOKEN)),
        getAuthURI(routerService));
    } finally {
      routerService.shutDown();
    }
  }

  @Test
  public void testAnnounceAddressWithoutPortConfig() throws Exception {
    HttpRouterService routerService = new AuthServerAnnounceTest.HttpRouterService(HOSTNAME, DISCOVERY_SERVICE);
    routerService.cConf.set(Constants.Security.AUTH_SERVER_ANNOUNCE_ADDRESS_DEPRECATED, ANNOUNCE_ADDRESS_WITHOUT_PORT);
    routerService.startUp();
    try {
      Assert.assertEquals(
        Collections.singletonList(String.format("http://%s:%d/%s", ANNOUNCE_ADDRESS_WITHOUT_PORT,
                                                routerService.cConf.getInt(Constants.Security.AUTH_SERVER_BIND_PORT),
                                                GrantAccessToken.Paths.GET_TOKEN)),
        getAuthURI(routerService));
    } finally {
      routerService.shutDown();
    }
  }

  @Test
  public void testAnnounceURLsConfig() throws Exception {
    HttpRouterService routerService = new AuthServerAnnounceTest.HttpRouterService(HOSTNAME, DISCOVERY_SERVICE);
    routerService.cConf.set(Constants.Security.AUTH_SERVER_ANNOUNCE_URLS, ANNOUNCE_URLS);
    // Deprecated property AUTH_SERVER_ANNOUNCE_ADDRESS_DEPRECATED is not used since AUTH_SERVER_ANNOUNCE_URLS is set
    routerService.cConf.set(Constants.Security.AUTH_SERVER_ANNOUNCE_ADDRESS_DEPRECATED, ANNOUNCE_ADDRESS_WITHOUT_PORT);
    routerService.startUp();
    try {
      List<String> expected = Lists.transform(Arrays.asList(ANNOUNCE_URLS.split(",")), new Function<String, String>() {
        @Override
        public String apply(String url) {
          return String.format("%s/%s", url, GrantAccessToken.Paths.GET_TOKEN);
        }
      });
      Assert.assertEquals(expected, getAuthURI(routerService));
    } finally {
      routerService.shutDown();
    }
  }

  private List<String> getAuthURI(HttpRouterService routerService) throws IOException, URISyntaxException {
    DefaultHttpClient client = new DefaultHttpClient();
    String url = resolveURI(Constants.Router.GATEWAY_DISCOVERY_NAME, "/v3/apps", routerService);
    HttpGet get = new HttpGet(url);
    HttpResponse response = client.execute(get);
    Map<String, List<String>> responseMap =
      GSON.fromJson(new InputStreamReader(response.getEntity().getContent()), TYPE);
    return responseMap.get("auth_uri");
  }

  private String resolveURI(String serviceName, String path, HttpRouterService routerService)
    throws URISyntaxException {
    return getBaseURI(serviceName, routerService).resolve(path).toASCIIString();
  }

  private URI getBaseURI(String serviceName, HttpRouterService routerService) throws URISyntaxException {
    int servicePort = routerService.lookupService(serviceName);
    return new URI(String.format("%s://%s:%d", "http", HOSTNAME, servicePort));
  }

  private static class HttpRouterService extends AbstractIdleService {
    private final String hostname;
    private final DiscoveryService discoveryService;
    private final Map<String, Integer> serviceMap = Maps.newHashMap();
    private CConfiguration cConf = CConfiguration.create();
    private NettyRouter router;

    private HttpRouterService(String hostname, DiscoveryService discoveryService) {
      this.hostname = hostname;
      this.discoveryService = discoveryService;
    }

    @Override
    protected void startUp() {
      SConfiguration sConfiguration = SConfiguration.create();
      Injector injector = Guice.createInjector(new SecurityModules().getInMemoryModules(),
                                               new DiscoveryRuntimeModule().getInMemoryModules(),
                                               new AppFabricTestModule(cConf));
      DiscoveryServiceClient discoveryServiceClient = injector.getInstance(DiscoveryServiceClient.class);
      AccessTokenTransformer accessTokenTransformer = injector.getInstance(AccessTokenTransformer.class);
      cConf.set(Constants.Router.ADDRESS, hostname);
      cConf.setInt(Constants.Router.ROUTER_PORT, 0);
      cConf.setInt(Constants.Router.CONNECTION_TIMEOUT_SECS, CONNECTION_IDLE_TIMEOUT_SECS);
      cConf.setBoolean(Constants.Security.ENABLED, true);

      router =
        new NettyRouter(cConf, sConfiguration, InetAddresses.forString(hostname),
                        new RouterServiceLookup(cConf, (DiscoveryServiceClient) discoveryService,
                                                new RouterPathLookup(), injector.getInstance(RouteStore.class)),
                        new MissingTokenValidator(), accessTokenTransformer, discoveryServiceClient);
      router.startAndWait();

      for (Map.Entry<Integer, String> entry : router.getServiceLookup().getServiceMap().entrySet()) {
        serviceMap.put(entry.getValue(), entry.getKey());
      }
    }

    @Override
    protected void shutDown() {
      router.stopAndWait();
    }

    public int lookupService(String serviceName) {
      return serviceMap.get(serviceName);
    }
  }

}
