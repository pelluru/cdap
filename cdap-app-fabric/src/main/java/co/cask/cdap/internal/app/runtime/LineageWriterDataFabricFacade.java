/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime;

import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.app.program.Program;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.data.ProgramContext;
import co.cask.cdap.data.ProgramContextAware;
import co.cask.cdap.data2.dataset2.DynamicDatasetCache;
import co.cask.cdap.data2.metadata.lineage.AccessType;
import co.cask.cdap.data2.metadata.writer.LineageWriter;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.queue.QueueConsumer;
import co.cask.cdap.data2.queue.QueueProducer;
import co.cask.cdap.data2.transaction.TransactionExecutorFactory;
import co.cask.cdap.data2.transaction.queue.QueueMetrics;
import co.cask.cdap.data2.transaction.stream.ForwardingStreamConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConsumerFactory;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.id.StreamId;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.tephra.TransactionAware;
import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionExecutor;
import org.apache.tephra.TransactionFailureException;

import java.io.IOException;

/**
 * Abstract base class for implementing DataFabricFacade.
 */
public final class LineageWriterDataFabricFacade implements DataFabricFacade, ProgramContextAware {

  private final DynamicDatasetCache datasetCache;
  private final QueueClientFactory queueClientFactory;
  private final StreamConsumerFactory streamConsumerFactory;
  private final TransactionExecutorFactory txExecutorFactory;
  private final Id.Program programId;
  private final LineageWriter lineageWriter;
  private volatile ProgramContext programContext;

  @Inject
  public LineageWriterDataFabricFacade(TransactionExecutorFactory txExecutorFactory,
                                       QueueClientFactory queueClientFactory,
                                       StreamConsumerFactory streamConsumerFactory,
                                       LineageWriter lineageWriter,
                                       @Assisted Program program,
                                       @Assisted DynamicDatasetCache datasetCache) {
    this.queueClientFactory = queueClientFactory;
    this.streamConsumerFactory = streamConsumerFactory;
    this.txExecutorFactory = txExecutorFactory;
    this.datasetCache = datasetCache;
    this.programId = program.getId().toId();
    this.lineageWriter = lineageWriter;
  }

  @Override
  public void setContext(ProgramContext programContext) {
    this.programContext = programContext;
    if (queueClientFactory instanceof ProgramContextAware) {
      ((ProgramContextAware) queueClientFactory).setContext(programContext);
    }
  }

  @Override
  public DatasetContext getDatasetContext() {
    return datasetCache;
  }

  @Override
  public TransactionContext createTransactionContext() throws TransactionFailureException {
    return datasetCache.newTransactionContext();
  }

  @Override
  public TransactionExecutor createTransactionExecutor() {
    return txExecutorFactory.createExecutor(datasetCache);
  }

  @Override
  public QueueProducer createProducer(QueueName queueName) throws IOException {
    return createProducer(queueName, QueueMetrics.NOOP_QUEUE_METRICS);
  }

  @Override
  public QueueConsumer createConsumer(QueueName queueName,
                                      ConsumerConfig consumerConfig, int numGroups) throws IOException {
    QueueConsumer consumer = queueClientFactory.createConsumer(queueName, consumerConfig, numGroups);
    if (consumer instanceof TransactionAware) {
      consumer = new CloseableQueueConsumer(datasetCache, consumer);
      datasetCache.addExtraTransactionAware((TransactionAware) consumer);
    }
    return consumer;
  }

  @Override
  public QueueProducer createProducer(QueueName queueName, QueueMetrics queueMetrics) throws IOException {
    QueueProducer producer = queueClientFactory.createProducer(queueName, queueMetrics);
    if (producer instanceof TransactionAware) {
      datasetCache.addExtraTransactionAware((TransactionAware) producer);
    }
    return producer;
  }

  @Override
  public StreamConsumer createStreamConsumer(StreamId streamName, ConsumerConfig consumerConfig) throws IOException {
    String namespace = String.format("%s.%s", programId.getApplicationId(), programId.getId());
    final StreamConsumer consumer = streamConsumerFactory.create(streamName, namespace, consumerConfig);

    datasetCache.addExtraTransactionAware(consumer);

    ProgramContext programContext = this.programContext;
    if (programContext != null) {
      lineageWriter.addAccess(programContext.getProgramRunId(), streamName,
                              AccessType.READ, programContext.getComponentId());
    }

    return new ForwardingStreamConsumer(consumer) {
      @Override
      public void close() throws IOException {
        super.close();
        datasetCache.removeExtraTransactionAware(consumer);
      }
    };
  }
}
