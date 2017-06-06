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

package co.cask.cdap.app.guice;

import co.cask.cdap.api.data.stream.StreamWriter;
import co.cask.cdap.app.store.RuntimeStore;
import co.cask.cdap.app.stream.DefaultStreamWriter;
import co.cask.cdap.app.stream.StreamWriterFactory;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.IOModule;
import co.cask.cdap.common.guice.KafkaClientModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.common.guice.ZKClientModule;
import co.cask.cdap.common.namespace.guice.NamespaceClientRuntimeModule;
import co.cask.cdap.data.runtime.DataFabricModules;
import co.cask.cdap.data.runtime.DataSetsModules;
import co.cask.cdap.data.stream.StreamAdminModules;
import co.cask.cdap.data.view.ViewAdminModules;
import co.cask.cdap.data2.audit.AuditModule;
import co.cask.cdap.data2.metadata.writer.LineageWriter;
import co.cask.cdap.data2.registry.RuntimeUsageRegistry;
import co.cask.cdap.explore.client.ExploreClient;
import co.cask.cdap.explore.client.ProgramDiscoveryExploreClient;
import co.cask.cdap.internal.app.queue.QueueReaderFactory;
import co.cask.cdap.internal.app.store.remote.RemoteLineageWriter;
import co.cask.cdap.internal.app.store.remote.RemoteRuntimeStore;
import co.cask.cdap.internal.app.store.remote.RemoteRuntimeUsageRegistry;
import co.cask.cdap.logging.guice.LoggingModules;
import co.cask.cdap.messaging.guice.MessagingClientModule;
import co.cask.cdap.metrics.guice.MetricsClientRuntimeModule;
import co.cask.cdap.notifications.feeds.client.NotificationFeedClientModule;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import co.cask.cdap.security.authorization.RemotePrivilegesManager;
import co.cask.cdap.security.guice.SecureStoreModules;
import co.cask.cdap.security.impersonation.CurrentUGIProvider;
import co.cask.cdap.security.impersonation.DefaultOwnerAdmin;
import co.cask.cdap.security.impersonation.OwnerAdmin;
import co.cask.cdap.security.impersonation.UGIProvider;
import co.cask.cdap.security.spi.authorization.PrivilegesManager;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import org.apache.hadoop.conf.Configuration;
import org.apache.twill.api.ServiceAnnouncer;
import org.apache.twill.api.TwillContext;
import org.apache.twill.common.Cancellable;

import java.net.InetAddress;
import javax.annotation.Nullable;

/**
 * Defines guice modules for distributed program runnables. For instance, AbstractProgramTwillRunnable, as well as
 * mapreduce tasks / spark executors.
 */
public class DistributedProgramRunnableModule {

  private final CConfiguration cConf;
  private final Configuration hConf;

  public DistributedProgramRunnableModule(CConfiguration cConf, Configuration hConf) {
    this.cConf = cConf;
    this.hConf = hConf;
  }

  // usable from any program runtime, such as mapreduce task, spark task, etc
  public Module createModule(final ProgramId programId, String runId, String instaneId,
                             @Nullable final String principal) {

    Module combined = getCombinedModules(programId, generateClient(programId, runId, instaneId));

    combined = addAuthenticationModule(principal, combined);

    return Modules.override(combined).with(new AbstractModule() {
      @Override
      protected void configure() {
        bind(LineageWriter.class).to(RemoteLineageWriter.class);
        bind(RuntimeUsageRegistry.class).to(RemoteRuntimeUsageRegistry.class).in(Scopes.SINGLETON);
      }
    });
  }

  private static String generateClient(ProgramId programId, String runId, String instanceId) {
    return String.format("%s.%s.%s", programId.toString(), runId, instanceId);
  }

  // TODO(terence) make this works for different mode
  // usable from anywhere a TwillContext is exposed
  public Module createModule(final TwillContext context, ProgramId programId, String runId, String instanceId,
                             @Nullable String principal) {
    return Modules.combine(createModule(programId, runId, instanceId, principal),
                           new AbstractModule() {
                             @Override
                             protected void configure() {

                               bind(InetAddress.class).annotatedWith(
                                 Names.named(Constants.Service.MASTER_SERVICES_BIND_ADDRESS))
                                 .toInstance(context.getHost());

                               bind(ServiceAnnouncer.class).toInstance(new ServiceAnnouncer() {
                                 @Override
                                 public Cancellable announce(String serviceName, int port) {
                                   return context.announce(serviceName, port);
                                 }

                                 @Override
                                 public Cancellable announce(String serviceName, int port, byte[] payload) {
                                   return context.announce(serviceName, port, payload);
                                 }
                               });
                             }
                           });
  }

  private Module addAuthenticationModule(@Nullable String principal, Module combined) {
    if (principal != null) {
      return Modules.combine(combined,
                             new AuthenticationContextModules().getProgramContainerModule(principal));
    }
    return Modules.combine(combined,
                           new AuthenticationContextModules().getProgramContainerModule());
  }

  private Module getCombinedModules(final ProgramId programId, String txClientId) {
    return Modules.combine(
      new ConfigModule(cConf, hConf),
      new IOModule(),
      new ZKClientModule(),
      new KafkaClientModule(),
      new MetricsClientRuntimeModule().getDistributedModules(),
      new MessagingClientModule(),
      new LocationRuntimeModule().getDistributedModules(),
      new LoggingModules().getDistributedModules(),
      new DiscoveryRuntimeModule().getDistributedModules(),
      new DataFabricModules(txClientId).getDistributedModules(),
      new DataSetsModules().getDistributedModules(),
      new ViewAdminModules().getDistributedModules(),
      new StreamAdminModules().getDistributedModules(),
      new NotificationFeedClientModule(),
      new AuditModule().getDistributedModules(),
      new NamespaceClientRuntimeModule().getDistributedModules(),
      new AuthorizationEnforcementModule().getDistributedModules(),
      new SecureStoreModules().getDistributedModules(),
      new AbstractModule() {
        @Override
        protected void configure() {
          // For Binding queue stuff
          bind(QueueReaderFactory.class).in(Scopes.SINGLETON);

          // For binding DataSet transaction stuff
          install(new DataFabricFacadeModule());

          bind(RuntimeStore.class).to(RemoteRuntimeStore.class);

          // For binding StreamWriter
          install(createStreamFactoryModule());

          // don't need to perform any impersonation from within user progarms
          bind(UGIProvider.class).to(CurrentUGIProvider.class).in(Scopes.SINGLETON);

          // bind PrivilegesManager to a remote implementation, so it does not need to instantiate the authorizer
          bind(PrivilegesManager.class).to(RemotePrivilegesManager.class);

          bind(OwnerAdmin.class).to(DefaultOwnerAdmin.class);

          // Bind ProgramId to the passed in instance programId so that we can retrieve it back later when needed.
          // For example see ProgramDiscoveryExploreClient.
          // Also binding to instance is fine here as the programId is guaranteed to not change throughout the
          // lifecycle of this program runnable
          bind(ProgramId.class).toInstance(programId);

          // bind explore client to ProgramDiscoveryExploreClient which is aware of the programId
          bind(ExploreClient.class).to(ProgramDiscoveryExploreClient.class).in(Scopes.SINGLETON);
        }
      }
    );
  }

  private Module createStreamFactoryModule() {
    return new PrivateModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().implement(StreamWriter.class, DefaultStreamWriter.class)
                  .build(StreamWriterFactory.class));
        expose(StreamWriterFactory.class);
      }
    };
  }
}
