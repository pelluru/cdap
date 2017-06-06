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

package co.cask.cdap.app.preview;

import co.cask.cdap.app.deploy.Manager;
import co.cask.cdap.app.deploy.ManagerFactory;
import co.cask.cdap.app.store.RuntimeStore;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.app.store.preview.PreviewStore;
import co.cask.cdap.common.namespace.NamespaceAdmin;
import co.cask.cdap.common.namespace.NamespaceQueryAdmin;
import co.cask.cdap.config.PreferencesStore;
import co.cask.cdap.data.stream.StreamCoordinatorClient;
import co.cask.cdap.data2.transaction.stream.StreamConsumerFactory;
import co.cask.cdap.data2.transaction.stream.inmemory.InMemoryStreamConsumerFactory;
import co.cask.cdap.explore.client.ExploreClient;
import co.cask.cdap.explore.client.MockExploreClient;
import co.cask.cdap.internal.app.deploy.pipeline.AppDeploymentInfo;
import co.cask.cdap.internal.app.deploy.pipeline.ApplicationWithPrograms;
import co.cask.cdap.internal.app.namespace.DefaultNamespaceAdmin;
import co.cask.cdap.internal.app.namespace.LocalStorageProviderNamespaceAdmin;
import co.cask.cdap.internal.app.namespace.NamespaceResourceDeleter;
import co.cask.cdap.internal.app.namespace.NoopNamespaceResourceDeleter;
import co.cask.cdap.internal.app.namespace.StorageProviderNamespaceAdmin;
import co.cask.cdap.internal.app.preview.DefaultDataTracerFactory;
import co.cask.cdap.internal.app.preview.DefaultPreviewRunner;
import co.cask.cdap.internal.app.runtime.artifact.ArtifactRepository;
import co.cask.cdap.internal.app.runtime.artifact.ArtifactStore;
import co.cask.cdap.internal.app.store.DefaultStore;
import co.cask.cdap.internal.app.store.preview.DefaultPreviewStore;
import co.cask.cdap.internal.pipeline.SynchronousPipelineFactory;
import co.cask.cdap.pipeline.PipelineFactory;
import co.cask.cdap.route.store.LocalRouteStore;
import co.cask.cdap.route.store.RouteStore;
import co.cask.cdap.scheduler.NoOpScheduler;
import co.cask.cdap.scheduler.Scheduler;
import co.cask.cdap.security.authorization.AuthorizerInstantiator;
import co.cask.cdap.security.impersonation.DefaultOwnerAdmin;
import co.cask.cdap.security.impersonation.OwnerAdmin;
import co.cask.cdap.security.impersonation.OwnerStore;
import co.cask.cdap.security.impersonation.UGIProvider;
import co.cask.cdap.security.impersonation.UnsupportedUGIProvider;
import co.cask.cdap.security.spi.authorization.AuthorizationEnforcer;
import co.cask.cdap.security.spi.authorization.PrivilegesManager;
import co.cask.cdap.store.DefaultOwnerStore;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/**
 * Provides bindings required to create injector for running preview.
 */
public class PreviewRunnerModule extends PrivateModule {

  private final ArtifactRepository artifactRepository;
  private final ArtifactStore artifactStore;
  private final AuthorizerInstantiator authorizerInstantiator;
  private final AuthorizationEnforcer authorizationEnforcer;
  private final PrivilegesManager privilegesManager;
  private final StreamCoordinatorClient streamCoordinatorClient;
  private final PreferencesStore preferencesStore;

  public PreviewRunnerModule(ArtifactRepository artifactRepository, ArtifactStore artifactStore,
                             AuthorizerInstantiator authorizerInstantiator, AuthorizationEnforcer authorizationEnforcer,
                             PrivilegesManager privilegesManager,
                             StreamCoordinatorClient streamCoordinatorClient, PreferencesStore preferencesStore) {
    this.artifactRepository = artifactRepository;
    this.artifactStore = artifactStore;
    this.authorizerInstantiator = authorizerInstantiator;
    this.authorizationEnforcer = authorizationEnforcer;
    this.privilegesManager = privilegesManager;
    this.streamCoordinatorClient = streamCoordinatorClient;
    this.preferencesStore = preferencesStore;
  }

  @Override
  protected void configure() {
    bind(ArtifactRepository.class).toInstance(artifactRepository);
    expose(ArtifactRepository.class);
    bind(ArtifactStore.class).toInstance(artifactStore);
    expose(ArtifactStore.class);
    bind(AuthorizerInstantiator.class).toInstance(authorizerInstantiator);
    expose(AuthorizerInstantiator.class);
    bind(AuthorizationEnforcer.class).toInstance(authorizationEnforcer);
    expose(AuthorizationEnforcer.class);
    bind(PrivilegesManager.class).toInstance(privilegesManager);
    expose(PrivilegesManager.class);
    bind(StreamConsumerFactory.class).to(InMemoryStreamConsumerFactory.class).in(Scopes.SINGLETON);
    expose(StreamConsumerFactory.class);
    bind(StreamCoordinatorClient.class).toInstance(streamCoordinatorClient);
    expose(StreamCoordinatorClient.class);
    bind(PreferencesStore.class).toInstance(preferencesStore);
    // bind explore client to mock.
    bind(ExploreClient.class).to(MockExploreClient.class);
    expose(ExploreClient.class);
    bind(StorageProviderNamespaceAdmin.class).to(LocalStorageProviderNamespaceAdmin.class);

    bind(PipelineFactory.class).to(SynchronousPipelineFactory.class);

    install(
      new FactoryModuleBuilder()
        .implement(new TypeLiteral<Manager<AppDeploymentInfo, ApplicationWithPrograms>>() { },
                   new TypeLiteral<PreviewApplicationManager<AppDeploymentInfo, ApplicationWithPrograms>>() { })
        .build(new TypeLiteral<ManagerFactory<AppDeploymentInfo, ApplicationWithPrograms>>() { })
    );

    bind(Store.class).to(DefaultStore.class);
    bind(RouteStore.class).to(LocalRouteStore.class).in(Scopes.SINGLETON);

    bind(UGIProvider.class).to(UnsupportedUGIProvider.class);
    expose(UGIProvider.class);

    bind(RuntimeStore.class).to(DefaultStore.class);
    expose(RuntimeStore.class);

    // we don't delete namespaces in preview as we just delete preview directory when its done
    bind(NamespaceResourceDeleter.class).to(NoopNamespaceResourceDeleter.class).in(Scopes.SINGLETON);
    bind(NamespaceAdmin.class).to(DefaultNamespaceAdmin.class).in(Scopes.SINGLETON);
    bind(NamespaceQueryAdmin.class).to(DefaultNamespaceAdmin.class).in(Scopes.SINGLETON);
    expose(NamespaceAdmin.class);
    expose(NamespaceQueryAdmin.class);

    bind(PreviewRunner.class).to(DefaultPreviewRunner.class).in(Scopes.SINGLETON);
    expose(PreviewRunner.class);

    bind(PreviewStore.class).to(DefaultPreviewStore.class).in(Scopes.SINGLETON);
    bind(Scheduler.class).to(NoOpScheduler.class);
    bind(co.cask.cdap.internal.app.runtime.schedule.Scheduler.class)
      .to(co.cask.cdap.internal.app.runtime.schedule.NoOpScheduler.class);

    bind(DataTracerFactory.class).to(DefaultDataTracerFactory.class);
    expose(DataTracerFactory.class);

    bind(OwnerStore.class).to(DefaultOwnerStore.class);
    expose(OwnerStore.class);
    bind(OwnerAdmin.class).to(DefaultOwnerAdmin.class);
    expose(OwnerAdmin.class);
  }
}
