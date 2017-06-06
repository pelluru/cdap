/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.service.http;

import co.cask.cdap.api.Transactional;
import co.cask.cdap.api.TxRunnable;
import co.cask.cdap.api.annotation.TransactionControl;
import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.service.http.HttpContentConsumer;
import co.cask.cdap.api.service.http.HttpContentProducer;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import co.cask.cdap.common.lang.ClassLoaders;
import co.cask.cdap.data2.transaction.Transactions;
import co.cask.http.BodyConsumer;
import co.cask.http.BodyProducer;
import co.cask.http.HttpResponder;
import com.google.common.collect.Multimap;
import org.apache.twill.common.Cancellable;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * An adapter class to delegate calls from {@link BodyConsumer} to {@link HttpContentConsumer}.
 */
final class BodyConsumerAdapter extends BodyConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(BodyConsumerAdapter.class);

  private final DelayedHttpServiceResponder responder;
  private final HttpContentConsumer delegate;
  private final Transactional transactional;
  private final ClassLoader programContextClassLoader;
  private final Cancellable contextReleaser;

  private boolean completed;

  /**
   * Constructs a new instance.
   *
   * @param responder the responder used for sending response back to client
   * @param delegate the {@link HttpContentConsumer} to delegate calls to
   * @param transactional a {@link Transactional} for executing transactional task
   * @param programContextClassLoader the context ClassLoader to use to execute user code
   * @param contextReleaser A {@link Cancellable} for returning the context back to the http server
   */
  BodyConsumerAdapter(DelayedHttpServiceResponder responder, HttpContentConsumer delegate,
                      Transactional transactional, ClassLoader programContextClassLoader, Cancellable contextReleaser) {
    this.responder = responder;
    this.delegate = delegate;
    this.transactional = transactional;
    this.programContextClassLoader = programContextClassLoader;
    this.contextReleaser = contextReleaser;
  }

  @Override
  public void chunk(final ChannelBuffer request, HttpResponder responder) {
    // Due to async nature of netty, chunk might get called even we try to close the connection in onError.
    if (completed) {
      return;
    }

    try {
      final ClassLoader oldClassLoader = ClassLoaders.setContextClassLoader(programContextClassLoader);
      try {
        delegate.onReceived(request.toByteBuffer(), transactional);
      } finally {
        ClassLoaders.setContextClassLoader(oldClassLoader);
      }
    } catch (Throwable t) {
      onError(t, this.responder);
    }
  }

  @Override
  public void finished(HttpResponder responder) {
    TransactionControl txCtrl = Transactions.getTransactionControl(
      TransactionControl.IMPLICIT, HttpContentConsumer.class, delegate, "onFinish", HttpServiceResponder.class);
    try {
      if (TransactionControl.IMPLICIT == txCtrl) {
        transactional.execute(new TxRunnable() {
          @Override
          public void run(DatasetContext context) throws Exception {
            delegate.onFinish(BodyConsumerAdapter.this.responder);
          }
        });
      } else {
        delegate.onFinish(BodyConsumerAdapter.this.responder);
      }
    } catch (Throwable t) {
      onError(t, this.responder);
      return;
    }

    // To the HttpContentConsumer, the call is completed even if it fails to send response back to client.
    completed = true;
    try {
      BodyConsumerAdapter.this.responder.execute();
    } finally {
      if (!this.responder.hasContentProducer()) {
        contextReleaser.cancel();
      }
    }
  }

  @Override
  public void handleError(final Throwable cause) {
    // When this method is called from netty-http, the response has already been sent, hence uses a no-op
    // DelayedHttpServiceResponder for the onError call.
    onError(cause, new DelayedHttpServiceResponder(responder, new ErrorBodyProducerFactory()) {
      @Override
      protected void doSend(int status, String contentType,
                            @Nullable ChannelBuffer content,
                            @Nullable HttpContentProducer contentProducer,
                            @Nullable Multimap<String, String> headers) {
        // no-op
      }

      @Override
      public void setTransactionFailureResponse(Throwable t) {
        // no-op
      }

      @Override
      public void execute(boolean keepAlive) {
        // no-op
      }

      @Override
      public boolean hasContentProducer() {
        // Always release the context at the end since it's not possible to send with a content producer
        return false;
      }
    });
  }

  /**
   * Calls the {@link HttpContentConsumer#onError(HttpServiceResponder, Throwable)} method from a transaction.
   */
  private void onError(final Throwable cause, final DelayedHttpServiceResponder responder) {
    if (completed) {
      return;
    }

    // To the HttpContentConsumer, once onError is called, no other methods will be triggered
    completed = true;
    TransactionControl txCtrl = Transactions.getTransactionControl(TransactionControl.IMPLICIT,
                                                                   HttpContentConsumer.class, delegate, "onError",
                                                                   HttpServiceResponder.class, Throwable.class);
    try {
      if (TransactionControl.IMPLICIT == txCtrl) {
        transactional.execute(new TxRunnable() {
          @Override
          public void run(DatasetContext context) throws Exception {
            delegate.onError(responder, cause);
          }
        });
      } else {
        delegate.onError(responder, cause);
      }
    } catch (Throwable t) {
      responder.setTransactionFailureResponse(t);
      LOG.warn("Exception in calling HttpContentConsumer.onError", t);
    } finally {
      try {
        responder.execute(false);
      } finally {
        if (!responder.hasContentProducer()) {
          contextReleaser.cancel();
        }
      }
    }
  }

  /**
   * A {@link BodyProducerFactory} to be used when {@link #handleError(Throwable)} is called.
   */
  private static final class ErrorBodyProducerFactory implements BodyProducerFactory {

    @Override
    public BodyProducer create(HttpContentProducer contentProducer, TransactionalHttpServiceContext serviceContext) {
      // It doesn't matter what it returns as it'll never get used
      // Returning a body producer that gives empty content
      return new BodyProducer() {
        @Override
        public ChannelBuffer nextChunk() throws Exception {
          return ChannelBuffers.EMPTY_BUFFER;
        }

        @Override
        public void finished() throws Exception {
          // no-op
        }

        @Override
        public void handleError(@Nullable Throwable throwable) {
          // no-op
        }
      };
    }
  }
}
