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

package co.cask.cdap.messaging.guice;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.runtime.RuntimeModule;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.data2.util.hbase.HBaseTableUtilFactory;
import co.cask.cdap.gateway.handlers.CommonHandlers;
import co.cask.cdap.messaging.MessagingService;
import co.cask.cdap.messaging.cache.MessageCache;
import co.cask.cdap.messaging.server.FetchHandler;
import co.cask.cdap.messaging.server.MessagingHttpService;
import co.cask.cdap.messaging.server.MetadataHandler;
import co.cask.cdap.messaging.server.StoreHandler;
import co.cask.cdap.messaging.service.CoreMessagingService;
import co.cask.cdap.messaging.store.MessageTable;
import co.cask.cdap.messaging.store.TableFactory;
import co.cask.cdap.messaging.store.cache.CachingTableFactory;
import co.cask.cdap.messaging.store.cache.DefaultMessageTableCacheProvider;
import co.cask.cdap.messaging.store.cache.MessageTableCacheProvider;
import co.cask.cdap.messaging.store.hbase.HBaseTableFactory;
import co.cask.cdap.messaging.store.leveldb.LevelDBTableFactory;
import co.cask.cdap.proto.id.TopicId;
import co.cask.http.HttpHandler;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

import javax.annotation.Nullable;

/**
 * Provides Guice modules for the messaging server.
 */
public class MessagingServerRuntimeModule extends RuntimeModule {

  @Override
  public Module getInMemoryModules() {
    return new LocalModule();
  }

  @Override
  public Module getStandaloneModules() {
    return new LocalModule();
  }

  @Override
  public Module getDistributedModules() {
    return new PrivateModule() {
      @Override
      protected void configure() {
        bind(HBaseTableUtil.class).toProvider(HBaseTableUtilProvider.class);
        bind(TableFactory.class)
          .annotatedWith(Names.named(CachingTableFactory.DELEGATE_TABLE_FACTORY))
          .to(HBaseTableFactory.class);

        // The cache must be in singleton scope
        bind(MessageTableCacheProvider.class).to(DefaultMessageTableCacheProvider.class).in(Scopes.SINGLETON);
        bind(TableFactory.class).to(CachingTableFactory.class).in(Scopes.SINGLETON);
        expose(TableFactory.class);

        bind(MessagingService.class).to(CoreMessagingService.class).in(Scopes.SINGLETON);
        expose(MessagingService.class);

        bindHandlers(binder(), Constants.MessagingSystem.HANDLER_BINDING_NAME);
        bind(MessagingHttpService.class).in(Scopes.SINGLETON);
        expose(MessagingHttpService.class);
      }
    };
  }

  private void bindHandlers(Binder binder, String bindingName) {
    Multibinder<HttpHandler> handlerBinder =
      Multibinder.newSetBinder(binder, HttpHandler.class, Names.named(bindingName));

    handlerBinder.addBinding().to(MetadataHandler.class);
    handlerBinder.addBinding().to(StoreHandler.class);
    handlerBinder.addBinding().to(FetchHandler.class);
    CommonHandlers.add(handlerBinder);
  }

  /**
   * Guice module being used in in memory as well as standalone mode.
   */
  private final class LocalModule extends PrivateModule {

    @Override
    protected void configure() {
      // No caching in local mode
      bind(MessageTableCacheProvider.class).toInstance(new MessageTableCacheProvider() {
        @Nullable
        @Override
        public MessageCache<MessageTable.Entry> getMessageCache(TopicId topicId) {
          return null;
        }
      });

      bind(TableFactory.class).to(LevelDBTableFactory.class).in(Scopes.SINGLETON);
      bind(MessagingService.class).to(CoreMessagingService.class).in(Scopes.SINGLETON);
      expose(MessagingService.class);

      // TODO: Because of CDAP-7688, we need to run MessagingHttpService even in local mode so that we
      // can use a custom http exception handler. When CDAP-7688 is resolved, uncomment the following
      // In local mode, we don't run the MessagingHttpService, but instead piggy back on app-fabric.
      // bindHandlers(binder(), Constants.AppFabric.HANDLERS_BINDING);

      // Begin workaround for CDAP-7688. Remove when it is resolved
      bindHandlers(binder(), Constants.MessagingSystem.HANDLER_BINDING_NAME);
      bind(MessagingHttpService.class).in(Scopes.SINGLETON);
      expose(MessagingHttpService.class);
      // End workaround for CDAP-7688
    }
  }

  /**
   * A guice provider for {@link HBaseTableUtil}. We don't use {@link HBaseTableUtilFactory} as a provider
   * directly because the {@code @Inject} constructor of {@link HBaseTableUtilFactory} requires a
   * injection of NamespaceQueryAdmin, which is unnecessary for Messaging service purpose.
   */
  private static final class HBaseTableUtilProvider implements Provider<HBaseTableUtil> {

    private final HBaseTableUtilFactory hBaseTableUtilFactory;

    @Inject
    HBaseTableUtilProvider(CConfiguration cConf) {
      this.hBaseTableUtilFactory = new HBaseTableUtilFactory(cConf);
    }

    @Override
    public HBaseTableUtil get() {
      return hBaseTableUtilFactory.get();
    }
  }
}
