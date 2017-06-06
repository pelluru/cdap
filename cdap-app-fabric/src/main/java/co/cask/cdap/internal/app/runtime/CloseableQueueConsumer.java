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

import co.cask.cdap.data2.dataset2.DynamicDatasetCache;
import co.cask.cdap.data2.queue.ForwardingQueueConsumer;
import co.cask.cdap.data2.queue.QueueConsumer;
import org.apache.tephra.TransactionAware;

import java.io.IOException;

/**
 * A {@link TransactionAware} {@link QueueConsumer} that removes itself from dataset context when closed.
 * All queue operations are forwarded to another {@link QueueConsumer}.
 */
final class CloseableQueueConsumer extends ForwardingQueueConsumer {

  private final DynamicDatasetCache context;

  CloseableQueueConsumer(DynamicDatasetCache context, QueueConsumer consumer) {
    super(consumer);
    this.context = context;
  }

  @Override
  public void close() throws IOException {
    try {
      consumer.close();
    } finally {
      context.removeExtraTransactionAware(this);
    }
  }
}
