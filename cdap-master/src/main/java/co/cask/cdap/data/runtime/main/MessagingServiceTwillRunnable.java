/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.data.runtime.main;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.IOModule;
import co.cask.cdap.common.guice.KafkaClientModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.common.guice.ZKClientModule;
import co.cask.cdap.common.logging.LoggingContextAccessor;
import co.cask.cdap.common.logging.ServiceLoggingContext;
import co.cask.cdap.common.namespace.guice.NamespaceClientRuntimeModule;
import co.cask.cdap.common.twill.AbstractMasterTwillRunnable;
import co.cask.cdap.logging.appender.LogAppenderInitializer;
import co.cask.cdap.logging.guice.LoggingModules;
import co.cask.cdap.messaging.MessagingService;
import co.cask.cdap.messaging.guice.MessagingServerRuntimeModule;
import co.cask.cdap.messaging.server.MessagingHttpService;
import co.cask.cdap.messaging.store.ForwardingTableFactory;
import co.cask.cdap.messaging.store.TableFactory;
import co.cask.cdap.messaging.store.hbase.HBaseTableFactory;
import co.cask.cdap.metrics.guice.MetricsClientRuntimeModule;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.conf.Configuration;
import org.apache.twill.api.TwillContext;
import org.apache.twill.api.TwillRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@link TwillRunnable} for messaging system.
 */
public class MessagingServiceTwillRunnable extends AbstractMasterTwillRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(MessagingServiceTwillRunnable.class);

  private Injector injector;

  MessagingServiceTwillRunnable(String name, String cConfName, String hConfName) {
    super(name, cConfName, hConfName);
  }

  @Override
  protected void addServices(List<? super Service> services) {
    MessagingService messagingService = injector.getInstance(MessagingService.class);
    if (messagingService instanceof Service) {
      services.add((Service) messagingService);
    }
    services.add(injector.getInstance(MessagingHttpService.class));
  }

  @Override
  protected Injector doInit(TwillContext context) {
    CConfiguration cConf = getCConfiguration();
    cConf.set(Constants.MessagingSystem.HTTP_SERVER_BIND_ADDRESS, context.getHost().getHostName());
    cConf.setInt(Constants.MessagingSystem.CONTAINER_INSTANCE_ID, context.getInstanceId());

    injector = createInjector(cConf, getConfiguration());
    injector.getInstance(LogAppenderInitializer.class).initialize();
    LoggingContextAccessor.setLoggingContext(new ServiceLoggingContext(NamespaceId.SYSTEM.getNamespace(),
                                                                       Constants.Logging.COMPONENT_NAME,
                                                                       Constants.Service.MESSAGING_SERVICE));
    HBaseTableFactory tableFactory = getHBaseTableFactory(injector.getInstance(TableFactory.class));

    // Upgrade the TMS Message and Payload Tables
    if (tableFactory != null) {
      try {
        tableFactory.upgradeMessageTable(cConf.get(Constants.MessagingSystem.MESSAGE_TABLE_NAME));
      } catch (IOException ex) {
        LOG.warn("Exception while trying to upgrade TMS MessageTable.", ex);
      }

      try {
        tableFactory.upgradePayloadTable(cConf.get(Constants.MessagingSystem.PAYLOAD_TABLE_NAME));
      } catch (IOException ex) {
        LOG.warn("Exception while trying to upgrade TMS PayloadTable.", ex);
      }
    }

    return injector;
  }

  @VisibleForTesting
  public static Injector createInjector(CConfiguration cConf, Configuration hConf) {
    return Guice.createInjector(
      new ConfigModule(cConf, hConf),
      new IOModule(),
      new ZKClientModule(),
      new KafkaClientModule(),
      new DiscoveryRuntimeModule().getDistributedModules(),
      new MetricsClientRuntimeModule().getDistributedModules(),
      new LoggingModules().getDistributedModules(),
      new LocationRuntimeModule().getDistributedModules(),
      new NamespaceClientRuntimeModule().getDistributedModules(),
      new AuthorizationEnforcementModule().getDistributedModules(),
      new AuthenticationContextModules().getMasterModule(),
      new MessagingServerRuntimeModule().getDistributedModules()
    );
  }

  @Nullable
  private HBaseTableFactory getHBaseTableFactory(TableFactory tableFactory) {
    TableFactory factory = tableFactory;

    while (!(factory instanceof HBaseTableFactory) && factory instanceof ForwardingTableFactory) {
      factory = ((ForwardingTableFactory) factory).getDelegate();
    }

    return factory instanceof HBaseTableFactory ? (HBaseTableFactory) factory : null;
  }
}
