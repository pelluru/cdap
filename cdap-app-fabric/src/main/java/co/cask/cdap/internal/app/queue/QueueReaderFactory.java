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

package co.cask.cdap.internal.app.queue;

import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.app.queue.QueueReader;
import co.cask.cdap.data2.queue.QueueConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConsumer;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.security.Action;
import co.cask.cdap.security.spi.authentication.AuthenticationContext;
import co.cask.cdap.security.spi.authorization.AuthorizationEnforcer;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.inject.Inject;

import java.nio.ByteBuffer;

/**
 *
 */
public final class QueueReaderFactory {

  private final AuthorizationEnforcer authorizationEnforcer;
  private final AuthenticationContext authenticationContext;

  @Inject
  public QueueReaderFactory(AuthorizationEnforcer authorizationEnforcer, AuthenticationContext authenticationContext) {
    this.authorizationEnforcer = authorizationEnforcer;
    this.authenticationContext = authenticationContext;
  }

  public <T> QueueReader<T> createQueueReader(Supplier<QueueConsumer> consumerSupplier,
                                              int batchSize, Function<ByteBuffer, T> decoder) {
    return new SingleQueue2Reader<>(consumerSupplier, batchSize, decoder);
  }

  public <T> QueueReader<T> createStreamReader(StreamId streamId, Supplier<StreamConsumer> consumerSupplier,
                                               int batchSize, Function<StreamEvent, T> transformer) throws Exception {
    authorizationEnforcer.enforce(streamId, authenticationContext.getPrincipal(), Action.READ);
    return new StreamQueueReader<>(streamId, consumerSupplier, batchSize, transformer, authenticationContext,
                                   authorizationEnforcer);
  }
}
