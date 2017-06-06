/*
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

import co.cask.cdap.common.ServiceBindException;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.conf.SConfiguration;
import co.cask.cdap.gateway.router.handlers.HttpRequestHandler;
import co.cask.cdap.gateway.router.handlers.HttpStatusRequestHandler;
import co.cask.cdap.gateway.router.handlers.SecurityAuthenticationHttpHandler;
import co.cask.cdap.security.auth.AccessTokenTransformer;
import co.cask.cdap.security.auth.TokenValidator;
import co.cask.cdap.security.tools.SSLHandlerFactory;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proxies request to a set of servers. Experimental.
 */
public class NettyRouter extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(NettyRouter.class);
  private static final int CLOSE_CHANNEL_TIMEOUT_SECS = 10;

  private final int serverBossThreadPoolSize;
  private final int serverWorkerThreadPoolSize;
  private final int serverConnectionBacklog;
  private final int clientBossThreadPoolSize;
  private final int clientWorkerThreadPoolSize;
  private final InetAddress hostname;
  private final Map<String, Integer> serviceToPortMap;
  private final ChannelGroup channelGroup = new DefaultChannelGroup("server channels");
  private final RouterServiceLookup serviceLookup;
  private final boolean securityEnabled;
  private final TokenValidator tokenValidator;
  private final AccessTokenTransformer accessTokenTransformer;
  private final CConfiguration configuration;
  private final String realm;
  private final boolean sslEnabled;
  private final SSLHandlerFactory sslHandlerFactory;
  private final int connectionTimeout;

  private Timer timer;
  private ServerBootstrap serverBootstrap;
  private ClientBootstrap clientBootstrap;
  private DiscoveryServiceClient discoveryServiceClient;

  @Inject
  public NettyRouter(CConfiguration cConf, SConfiguration sConf, @Named(Constants.Router.ADDRESS) InetAddress hostname,
                     RouterServiceLookup serviceLookup, TokenValidator tokenValidator,
                     AccessTokenTransformer accessTokenTransformer,
                     DiscoveryServiceClient discoveryServiceClient) {
    this.serverBossThreadPoolSize = cConf.getInt(Constants.Router.SERVER_BOSS_THREADS);
    this.serverWorkerThreadPoolSize = cConf.getInt(Constants.Router.SERVER_WORKER_THREADS);
    this.serverConnectionBacklog = cConf.getInt(Constants.Router.BACKLOG_CONNECTIONS);
    this.clientBossThreadPoolSize = cConf.getInt(Constants.Router.CLIENT_BOSS_THREADS);
    this.clientWorkerThreadPoolSize = cConf.getInt(Constants.Router.CLIENT_WORKER_THREADS);
    this.hostname = hostname;
    this.serviceToPortMap = new HashMap<>();
    this.serviceLookup = serviceLookup;
    this.securityEnabled = cConf.getBoolean(Constants.Security.ENABLED, false);
    this.realm = cConf.get(Constants.Security.CFG_REALM);
    this.tokenValidator = tokenValidator;
    this.accessTokenTransformer = accessTokenTransformer;
    this.discoveryServiceClient = discoveryServiceClient;
    this.configuration = cConf;
    this.sslEnabled = cConf.getBoolean(Constants.Security.SSL.EXTERNAL_ENABLED);
    if (isSSLEnabled()) {
      this.serviceToPortMap.put(Constants.Router.GATEWAY_DISCOVERY_NAME,
                                cConf.getInt(Constants.Router.ROUTER_SSL_PORT));

      File keystore;
      try {
        keystore = new File(sConf.get(Constants.Security.Router.SSL_KEYSTORE_PATH));
      } catch (Throwable e) {
        throw new RuntimeException("SSL is enabled but the keystore file could not be read. Please verify that the " +
                                     "keystore file exists and the path is set correctly : "
                                     + sConf.get(Constants.Security.Router.SSL_KEYSTORE_PATH));
      }
      this.sslHandlerFactory = new SSLHandlerFactory(keystore,
                                                     sConf.get(Constants.Security.Router.SSL_KEYSTORE_TYPE,
                                                               Constants.Security.Router.DEFAULT_SSL_KEYSTORE_TYPE),
                                                     sConf.get(Constants.Security.Router.SSL_KEYSTORE_PASSWORD),
                                                     sConf.get(Constants.Security.Router.SSL_KEYPASSWORD));
    } else {
      this.serviceToPortMap.put(Constants.Router.GATEWAY_DISCOVERY_NAME, cConf.getInt(Constants.Router.ROUTER_PORT));
      this.sslHandlerFactory = null;
    }
    this.connectionTimeout = cConf.getInt(Constants.Router.CONNECTION_TIMEOUT_SECS);
    LOG.info("Using connection timeout: {}", connectionTimeout);
    LOG.info("Service to Port Mapping - {}", this.serviceToPortMap);
  }

  @Override
  protected void startUp() throws ServiceBindException {
    ChannelUpstreamHandler connectionTracker = new SimpleChannelUpstreamHandler() {
      @Override
      public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception {
        channelGroup.add(e.getChannel());
        super.channelOpen(ctx, e);
      }
    };

    tokenValidator.startAndWait();
    timer = new HashedWheelTimer(
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("router-idle-event-generator-timer").build());
    bootstrapClient(connectionTracker);

    bootstrapServer(connectionTracker);
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Stopping Netty Router...");

    try {
      if (!channelGroup.close().await(CLOSE_CHANNEL_TIMEOUT_SECS, TimeUnit.SECONDS)) {
        LOG.warn("Timeout when closing all channels.");
      }
    } finally {
      serverBootstrap.shutdown();
      clientBootstrap.shutdown();
      clientBootstrap.releaseExternalResources();
      serverBootstrap.releaseExternalResources();
      tokenValidator.stopAndWait();
      timer.stop();
    }

    LOG.info("Stopped Netty Router.");
  }

  /** @noinspection NullableProblems */
  @Override
  protected Executor executor(final State state) {
    final AtomicInteger id = new AtomicInteger();
    return new Executor() {
      @Override
      public void execute(Runnable runnable) {
        Thread t = new Thread(runnable, String.format("NettyRouter-%d", id.incrementAndGet()));
        t.start();
      }
    };
  }

  public RouterServiceLookup getServiceLookup() {
    return serviceLookup;
  }

  private ExecutorService createExecutorService(int threadPoolSize, String name) {
    return Executors.newFixedThreadPool(threadPoolSize,
                                        new ThreadFactoryBuilder()
                                          .setDaemon(true)
                                          .setNameFormat(name)
                                          .build());
  }

  private void bootstrapServer(final ChannelUpstreamHandler connectionTracker) throws ServiceBindException {
    ExecutorService serverBossExecutor = createExecutorService(serverBossThreadPoolSize,
                                                               "router-server-boss-thread-%d");
    ExecutorService serverWorkerExecutor = createExecutorService(serverWorkerThreadPoolSize,
                                                                 "router-server-worker-thread-%d");
    serverBootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(serverBossExecutor, serverWorkerExecutor));
    serverBootstrap.setOption("backlog", serverConnectionBacklog);
    serverBootstrap.setOption("child.bufferFactory", new DirectChannelBufferFactory());

    // Setup the pipeline factory
    serverBootstrap.setPipelineFactory(
      new ChannelPipelineFactory() {
        @Override
        public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline pipeline = Channels.pipeline();
          if (isSSLEnabled()) {
            // Add SSLHandler is SSL is enabled
            pipeline.addLast("ssl", sslHandlerFactory.create());
          }
          pipeline.addLast("tracker", connectionTracker);
          pipeline.addLast("http-response-encoder", new HttpResponseEncoder());
          pipeline.addLast("http-decoder", new HttpRequestDecoder());
          pipeline.addLast("http-status-request-handler", new HttpStatusRequestHandler());
          if (securityEnabled) {
            pipeline.addLast("access-token-authenticator", new SecurityAuthenticationHttpHandler(
              realm, tokenValidator, configuration, accessTokenTransformer, discoveryServiceClient));
          }
          // for now there's only one hardcoded rule, but if there will be more, we may want it generic and configurable
          pipeline.addLast("http-request-handler",
                           new HttpRequestHandler(clientBootstrap, serviceLookup, ImmutableList.<ProxyRule>of()));
          return pipeline;
        }
      }
    );

    // Start listening on ports.
    ImmutableMap.Builder<Integer, String> serviceMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Integer> forward : serviceToPortMap.entrySet()) {
      int port = forward.getValue();
      String service = forward.getKey();

      String boundService = serviceLookup.getService(port);
      if (boundService != null) {
        LOG.warn("Port {} is already configured to service {}, ignoring forward for service {}",
                 port, boundService, service);
        continue;
      }

      InetSocketAddress bindAddress = new InetSocketAddress(hostname, port);
      LOG.info("Starting Netty Router for service {} on address {}...", service, bindAddress);

      try {
        Channel channel = serverBootstrap.bind(bindAddress);
        InetSocketAddress boundAddress = (InetSocketAddress) channel.getLocalAddress();
        serviceMapBuilder.put(boundAddress.getPort(), service);
        channelGroup.add(channel);

        // Update service map
        serviceLookup.updateServiceMap(serviceMapBuilder.build());

        LOG.info("Started Netty Router for service {} on address {}.", service, boundAddress);
      } catch (ChannelException e) {
        if ((Throwables.getRootCause(e) instanceof BindException)) {
          throw new ServiceBindException("Router", hostname.getCanonicalHostName(), port, e);
        }

        throw e;
      }
    }
  }

  private void bootstrapClient(final ChannelUpstreamHandler connectionTracker) {
    ExecutorService clientBossExecutor = createExecutorService(clientBossThreadPoolSize,
                                                               "router-client-boss-thread-%d");
    ExecutorService clientWorkerExecutor = createExecutorService(clientWorkerThreadPoolSize,
                                                                 "router-client-worker-thread-%d");

    clientBootstrap = new ClientBootstrap(
      new NioClientSocketChannelFactory(
        new NioClientBossPool(clientBossExecutor, clientBossThreadPoolSize),
        new NioWorkerPool(clientWorkerExecutor, clientWorkerThreadPoolSize)));

    ChannelPipelineFactory pipelineFactory = new ClientChannelPipelineFactory(connectionTracker, connectionTimeout,
                                                                              timer);
    clientBootstrap.setPipelineFactory(pipelineFactory);
    clientBootstrap.setOption("bufferFactory", new DirectChannelBufferFactory());
  }

  private boolean isSSLEnabled() {
    return sslEnabled;
  }
}
