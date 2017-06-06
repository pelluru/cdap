/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

package co.cask.cdap.data.runtime;

import co.cask.cdap.api.dataset.module.DatasetDefinitionRegistry;
import co.cask.cdap.common.runtime.RuntimeModule;
import co.cask.cdap.data2.datafabric.dataset.RemoteDatasetFramework;
import co.cask.cdap.data2.dataset2.DatasetDefinitionRegistryFactory;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.DefaultDatasetDefinitionRegistry;
import co.cask.cdap.data2.dataset2.InMemoryDatasetFramework;
import co.cask.cdap.data2.metadata.lineage.LineageStore;
import co.cask.cdap.data2.metadata.lineage.LineageStoreReader;
import co.cask.cdap.data2.metadata.lineage.LineageStoreWriter;
import co.cask.cdap.data2.metadata.store.DefaultMetadataStore;
import co.cask.cdap.data2.metadata.store.MetadataStore;
import co.cask.cdap.data2.metadata.store.NoOpMetadataStore;
import co.cask.cdap.data2.metadata.writer.BasicLineageWriter;
import co.cask.cdap.data2.metadata.writer.LineageWriter;
import co.cask.cdap.data2.metadata.writer.LineageWriterDatasetFramework;
import co.cask.cdap.data2.registry.DefaultUsageRegistry;
import co.cask.cdap.data2.registry.RuntimeUsageRegistry;
import co.cask.cdap.data2.registry.UsageRegistry;
import co.cask.cdap.security.impersonation.OwnerStore;
import co.cask.cdap.store.DefaultOwnerStore;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

/**
 * DataSets framework bindings
 */
public class DataSetsModules extends RuntimeModule {

  public static final String BASE_DATASET_FRAMEWORK = "basicDatasetFramework";

  @Override
  public Module getInMemoryModules() {
    return new PrivateModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder()
                  .implement(DatasetDefinitionRegistry.class, DefaultDatasetDefinitionRegistry.class)
                  .build(DatasetDefinitionRegistryFactory.class));

        bind(MetadataStore.class).to(NoOpMetadataStore.class);
        expose(MetadataStore.class);

        bind(DatasetFramework.class)
          .annotatedWith(Names.named(BASE_DATASET_FRAMEWORK))
          .to(InMemoryDatasetFramework.class).in(Scopes.SINGLETON);

        bind(LineageStoreReader.class).to(LineageStore.class);
        bind(LineageStoreWriter.class).to(LineageStore.class);
        // Need to expose LineageStoreReader as it's being used by the LineageHandler (through LineageAdmin)
        expose(LineageStoreReader.class);

        bind(LineageWriter.class).to(BasicLineageWriter.class);
        expose(LineageWriter.class);

        bind(UsageRegistry.class).to(DefaultUsageRegistry.class).in(Scopes.SINGLETON);
        expose(UsageRegistry.class);
        bind(RuntimeUsageRegistry.class).to(DefaultUsageRegistry.class).in(Scopes.SINGLETON);
        expose(RuntimeUsageRegistry.class);
        bind(DefaultUsageRegistry.class).in(Scopes.SINGLETON);

        bind(DatasetFramework.class).to(LineageWriterDatasetFramework.class);
        expose(DatasetFramework.class);

        bind(DefaultOwnerStore.class).in(Scopes.SINGLETON);
        bind(OwnerStore.class).to(DefaultOwnerStore.class);
        expose(OwnerStore.class);
      }
    };
  }

  @Override
  public Module getStandaloneModules() {
    return getModule();
  }

  @Override
  public Module getDistributedModules() {
    return getModule();
  }

  private Module getModule() {
    return new PrivateModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder()
                  .implement(DatasetDefinitionRegistry.class, DefaultDatasetDefinitionRegistry.class)
                  .build(DatasetDefinitionRegistryFactory.class));

        bind(MetadataStore.class).to(DefaultMetadataStore.class);
        expose(MetadataStore.class);

        bind(DatasetFramework.class)
          .annotatedWith(Names.named(BASE_DATASET_FRAMEWORK))
          .to(RemoteDatasetFramework.class);

        bind(LineageStoreReader.class).to(LineageStore.class);
        bind(LineageStoreWriter.class).to(LineageStore.class);
        // Need to expose LineageStoreReader as it's being used by the LineageHandler (through LineageAdmin)
        expose(LineageStoreReader.class);

        bind(LineageWriter.class).to(BasicLineageWriter.class);
        expose(LineageWriter.class);

        bind(UsageRegistry.class).to(DefaultUsageRegistry.class).in(Scopes.SINGLETON);
        expose(UsageRegistry.class);
        bind(RuntimeUsageRegistry.class).to(DefaultUsageRegistry.class).in(Scopes.SINGLETON);
        expose(RuntimeUsageRegistry.class);
        bind(DefaultUsageRegistry.class).in(Scopes.SINGLETON);

        bind(DatasetFramework.class).to(LineageWriterDatasetFramework.class);
        expose(DatasetFramework.class);

        bind(DefaultOwnerStore.class).in(Scopes.SINGLETON);
        bind(OwnerStore.class).to(DefaultOwnerStore.class);
        expose(OwnerStore.class);
      }
    };
  }
}
