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

package co.cask.cdap.internal.app.services;

import co.cask.cdap.api.Resources;
import co.cask.cdap.api.metrics.MetricsContext;
import co.cask.cdap.api.service.Service;
import co.cask.cdap.api.service.ServiceConfigurer;
import co.cask.cdap.api.service.ServiceSpecification;
import co.cask.cdap.api.service.http.HttpServiceContext;
import co.cask.cdap.api.service.http.HttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceHandlerSpecification;
import co.cask.cdap.common.metrics.NoOpMetricsCollectionService;
import co.cask.cdap.internal.app.DefaultPluginConfigurer;
import co.cask.cdap.internal.app.runtime.artifact.ArtifactRepository;
import co.cask.cdap.internal.app.runtime.plugin.PluginInstantiator;
import co.cask.cdap.internal.app.runtime.service.http.DelegatorContext;
import co.cask.cdap.internal.app.runtime.service.http.HttpHandlerFactory;
import co.cask.cdap.proto.Id;
import co.cask.http.HttpHandler;
import co.cask.http.NettyHttpService;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import org.apache.twill.common.Cancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A default implementation of {@link ServiceConfigurer}.
 */
public class DefaultServiceConfigurer extends DefaultPluginConfigurer implements ServiceConfigurer {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceConfigurer.class);
  private final String className;
  private final Id.Artifact artifactId;
  private final ArtifactRepository artifactRepository;
  private final PluginInstantiator pluginInstantiator;

  private String name;
  private String description;
  private List<HttpServiceHandler> handlers;
  private Resources resources;
  private int instances;

  /**
   * Create an instance of {@link DefaultServiceConfigurer}
   */
  public DefaultServiceConfigurer(Service service, Id.Namespace namespace, Id.Artifact artifactId,
                                  ArtifactRepository artifactRepository, PluginInstantiator pluginInstantiator) {
    super(namespace, artifactId, artifactRepository, pluginInstantiator);
    this.className = service.getClass().getName();
    this.name = service.getClass().getSimpleName();
    this.description = "";
    this.handlers = Lists.newArrayList();
    this.resources = new Resources();
    this.instances = 1;
    this.artifactId = artifactId;
    this.artifactRepository = artifactRepository;
    this.pluginInstantiator = pluginInstantiator;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public void addHandlers(Iterable<? extends HttpServiceHandler> serviceHandlers) {
    Iterables.addAll(handlers, serviceHandlers);
  }

  @Override
  public void setInstances(int instances) {
    Preconditions.checkArgument(instances > 0, "Instances must be > 0.");
    this.instances = instances;
  }

  @Override
  public void setResources(Resources resources) {
    Preconditions.checkArgument(resources != null, "Resources cannot be null.");
    this.resources = resources;
  }

  public ServiceSpecification createSpecification() {
    Map<String, HttpServiceHandlerSpecification> handleSpecs = createHandlerSpecs(handlers);
    return new ServiceSpecification(className, name, description, handleSpecs, resources, instances);
  }

  /**
   * Constructs HttpServiceSpecifications for each of the handlers in the {@param handlers} list.
   * Also performs verifications on these handlers (that a NettyHttpService can be constructed from them).
   */
  private Map<String, HttpServiceHandlerSpecification> createHandlerSpecs(List<? extends HttpServiceHandler> handlers) {
    verifyHandlers(handlers);
    Map<String, HttpServiceHandlerSpecification> handleSpecs = Maps.newHashMap();
    for (HttpServiceHandler handler : handlers) {
      DefaultHttpServiceHandlerConfigurer configurer = new DefaultHttpServiceHandlerConfigurer(
        handler, deployNamespace, artifactId, artifactRepository, pluginInstantiator);
      handler.configure(configurer);
      HttpServiceHandlerSpecification spec = configurer.createSpecification();
      Preconditions.checkArgument(!handleSpecs.containsKey(spec.getName()),
                                  "Handler with name %s already existed.", spec.getName());
      handleSpecs.put(spec.getName(), spec);
      addStreams(configurer.getStreams());
      addDatasetModules(configurer.getDatasetModules());
      addDatasetSpecs(configurer.getDatasetSpecs());
      addPlugins(configurer.getPlugins());
    }
    return handleSpecs;
  }

  private void verifyHandlers(List<? extends HttpServiceHandler> handlers) {
    Preconditions.checkArgument(!Iterables.isEmpty(handlers), "Service %s should have at least one handler", name);
    try {
      List<HttpHandler> httpHandlers = Lists.newArrayList();
      for (HttpServiceHandler handler : handlers) {
        httpHandlers.add(createHttpHandler(handler));
      }

      // Constructs a NettyHttpService, to verify that the handlers passed in by the user are valid.
      NettyHttpService.builder()
        .addHttpHandlers(httpHandlers)
        .build();
    } catch (Throwable t) {
      String errMessage = String.format("Invalid handlers in service: %s.", name);
      LOG.error(errMessage, t);
      throw new IllegalArgumentException(errMessage, t);
    }
  }

  private <T extends HttpServiceHandler> HttpHandler createHttpHandler(T handler) {
    MetricsContext noOpsMetricsContext =
      new NoOpMetricsCollectionService().getContext(new HashMap<String, String>());
    HttpHandlerFactory factory = new HttpHandlerFactory("", noOpsMetricsContext);
    @SuppressWarnings("unchecked")
    TypeToken<T> type = (TypeToken<T>) TypeToken.of(handler.getClass());
    return factory.createHttpHandler(type, new VerificationDelegateContext<>(handler));
  }

  private static final class VerificationDelegateContext<T extends HttpServiceHandler> implements DelegatorContext<T> {

    private final T handler;

    private VerificationDelegateContext(T handler) {
      this.handler = handler;
    }

    @Override
    public T getHandler() {
      return handler;
    }

    @Override
    public HttpServiceContext getServiceContext() {
      // Never used. (It's only used during server runtime, which we don't verify).
      return null;
    }

    @Override
    public Cancellable capture() {
      return new Cancellable() {
        @Override
        public void cancel() {
          // no-op
        }
      };
    }
  }
}
