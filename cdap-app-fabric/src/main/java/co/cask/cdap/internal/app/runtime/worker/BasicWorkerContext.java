/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.worker;

import co.cask.cdap.api.data.stream.StreamBatchWriter;
import co.cask.cdap.api.data.stream.StreamWriter;
import co.cask.cdap.api.metrics.MetricsCollectionService;
import co.cask.cdap.api.security.store.SecureStore;
import co.cask.cdap.api.security.store.SecureStoreManager;
import co.cask.cdap.api.stream.StreamEventData;
import co.cask.cdap.api.worker.WorkerContext;
import co.cask.cdap.api.worker.WorkerSpecification;
import co.cask.cdap.app.program.Program;
import co.cask.cdap.app.runtime.ProgramOptions;
import co.cask.cdap.app.stream.StreamWriterFactory;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.internal.app.runtime.AbstractContext;
import co.cask.cdap.internal.app.runtime.plugin.PluginInstantiator;
import co.cask.cdap.logging.context.WorkerLoggingContext;
import co.cask.cdap.messaging.MessagingService;
import co.cask.cdap.proto.Id;
import com.google.common.collect.ImmutableMap;
import org.apache.tephra.TransactionSystemClient;
import org.apache.twill.api.RunId;
import org.apache.twill.discovery.DiscoveryServiceClient;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link WorkerContext}
 */
final class BasicWorkerContext extends AbstractContext implements WorkerContext {

  private final WorkerSpecification specification;
  private final int instanceId;
  private final LoggingContext loggingContext;
  private final StreamWriter streamWriter;
  private volatile int instanceCount;

  BasicWorkerContext(WorkerSpecification spec, Program program, ProgramOptions programOptions,
                     CConfiguration cConf, int instanceId, int instanceCount,
                     MetricsCollectionService metricsCollectionService,
                     DatasetFramework datasetFramework,
                     TransactionSystemClient transactionSystemClient,
                     DiscoveryServiceClient discoveryServiceClient,
                     StreamWriterFactory streamWriterFactory,
                     @Nullable PluginInstantiator pluginInstantiator,
                     SecureStore secureStore,
                     SecureStoreManager secureStoreManager,
                     MessagingService messagingService) {
    super(program, programOptions, cConf, spec.getDatasets(),
          datasetFramework, transactionSystemClient, discoveryServiceClient, true,
          metricsCollectionService, ImmutableMap.of(Constants.Metrics.Tag.INSTANCE_ID, String.valueOf(instanceId)),
          secureStore, secureStoreManager, messagingService, pluginInstantiator);

    this.specification = spec;
    this.instanceId = instanceId;
    this.instanceCount = instanceCount;
    this.loggingContext = createLoggingContext(program.getId().toId(), getRunId());
    this.streamWriter = streamWriterFactory.create(new Id.Run(program.getId().toId(), getRunId().getId()),
                                                   getOwners(),
                                                   retryStrategy);
  }

  private LoggingContext createLoggingContext(Id.Program programId, RunId runId) {
    return new WorkerLoggingContext(programId.getNamespaceId(), programId.getApplicationId(), programId.getId(),
                                    runId.getId(), String.valueOf(getInstanceId()));
  }

  public LoggingContext getLoggingContext() {
    return loggingContext;
  }

  @Override
  public WorkerSpecification getSpecification() {
    return specification;
  }

  @Override
  public int getInstanceCount() {
    return instanceCount;
  }

  @Override
  public int getInstanceId() {
    return instanceId;
  }

  public void setInstanceCount(int instanceCount) {
    this.instanceCount = instanceCount;
  }

  @Override
  public void write(String stream, String data) throws IOException {
    streamWriter.write(stream, data);
  }

  @Override
  public void write(String stream, String data, Map<String, String> headers) throws IOException {
    streamWriter.write(stream, data, headers);
  }

  @Override
  public void write(String stream, ByteBuffer data) throws IOException {
    streamWriter.write(stream, data);
  }

  @Override
  public void write(String stream, StreamEventData data) throws IOException {
    streamWriter.write(stream, data);
  }

  @Override
  public void writeFile(String stream, File file, String contentType) throws IOException {
    streamWriter.writeFile(stream, file, contentType);
  }

  @Override
  public StreamBatchWriter createBatchWriter(String stream, String contentType) throws IOException {
    return streamWriter.createBatchWriter(stream, contentType);
  }
}
