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

package co.cask.cdap.internal.app.runtime;

import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.transaction.stream.StreamConsumer;
import co.cask.cdap.proto.id.StreamId;
import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionExecutor;
import org.apache.tephra.TransactionFailureException;

import java.io.IOException;

/**
 *
 */
public interface DataFabricFacade extends QueueClientFactory {

  DatasetContext getDatasetContext();

  TransactionContext createTransactionContext() throws TransactionFailureException;

  TransactionExecutor createTransactionExecutor();

  StreamConsumer createStreamConsumer(StreamId streamName, ConsumerConfig consumerConfig) throws IOException;
}
