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

package co.cask.cdap.test.internal;

import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.discovery.RandomEndpointStrategy;
import co.cask.cdap.internal.AppFabricClient;
import co.cask.cdap.proto.ServiceInstances;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.test.AbstractProgramManager;
import co.cask.cdap.test.MetricsManager;
import co.cask.cdap.test.ServiceManager;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.apache.twill.discovery.Discoverable;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.apache.twill.discovery.ServiceDiscovered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A default implementation of {@link ServiceManager}.
 */
public class DefaultServiceManager extends AbstractProgramManager<ServiceManager> implements ServiceManager {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceManager.class);

  private final String namespace;
  private final String applicationId;
  private final String serviceName;

  private final DiscoveryServiceClient discoveryServiceClient;
  private final AppFabricClient appFabricClient;

  private final MetricsManager metricsManager;

  public DefaultServiceManager(ProgramId programId,
                               AppFabricClient appFabricClient, DiscoveryServiceClient discoveryServiceClient,
                               DefaultApplicationManager applicationManager, MetricsManager metricsManager) {
    super(programId, applicationManager);
    this.namespace = programId.getNamespace();
    this.applicationId = programId.getApplication();
    this.serviceName = programId.getProgram();

    this.discoveryServiceClient = discoveryServiceClient;
    this.appFabricClient = appFabricClient;
    this.metricsManager = metricsManager;
  }

  @Override
  public void setInstances(int instances) {
    Preconditions.checkArgument(instances > 0, "Instance count should be > 0.");
    try {
      appFabricClient.setServiceInstances(namespace, applicationId, serviceName, instances);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public int getRequestedInstances() {
    ServiceInstances instances = getInstances();
    return instances.getRequested();
  }

  @Override
  public int getProvisionedInstances() {
    ServiceInstances instances = getInstances();
    return instances.getProvisioned();
  }

  private ServiceInstances getInstances() {
    try {
      return appFabricClient.getServiceInstances(namespace, applicationId, serviceName);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public URL getServiceURL() {
    return getServiceURL(1, TimeUnit.SECONDS);
  }

  @Override
  public URL getServiceURL(long timeout, TimeUnit timeoutUnit) {
    String discoveryName = String.format("service.%s.%s.%s", namespace, applicationId, serviceName);
    ServiceDiscovered discovered = discoveryServiceClient.discover(discoveryName);
    return createURL(new RandomEndpointStrategy(discovered).pick(timeout, timeoutUnit), applicationId, serviceName);
  }

  @Override
  public RuntimeMetrics getMetrics() {
    return metricsManager.getServiceMetrics(namespace, applicationId, serviceName);
  }

  @Nullable
  private URL createURL(@Nullable Discoverable discoverable, String applicationId, String serviceName) {
    if (discoverable == null) {
      return null;
    }
    InetSocketAddress address = discoverable.getSocketAddress();
    String scheme = Arrays.equals(Constants.Security.SSL_URI_SCHEME.getBytes(), discoverable.getPayload()) ?
      Constants.Security.SSL_URI_SCHEME : Constants.Security.URI_SCHEME;
    String path = String.format("%s%s:%d%s/namespaces/%s/apps/%s/services/%s/methods/", scheme,
                                address.getHostName(), address.getPort(),
                                Constants.Gateway.API_VERSION_3, namespace, applicationId, serviceName);
    try {
      return new URL(path);
    } catch (MalformedURLException e) {
      LOG.error("Got exception while creating serviceURL", e);
      return null;
    }
  }
}
