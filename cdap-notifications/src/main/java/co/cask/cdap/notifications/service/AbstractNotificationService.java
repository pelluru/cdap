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

package co.cask.cdap.notifications.service;

import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.transaction.TransactionSystemClientService;
import co.cask.cdap.notifications.NotificationFeedInfoDeserializer;
import co.cask.cdap.notifications.feeds.NotificationFeedException;
import co.cask.cdap.notifications.feeds.NotificationFeedManager;
import co.cask.cdap.notifications.feeds.NotificationFeedNotFoundException;
import co.cask.cdap.notifications.service.inmemory.InMemoryNotificationService;
import co.cask.cdap.proto.id.NotificationFeedId;
import co.cask.cdap.proto.notification.NotificationFeedInfo;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.apache.twill.common.Cancellable;
import org.apache.twill.common.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Common implementation of the the {@link NotificationService} that handles the subscriptions to all the notification
 * feeds for the current process and that has the ability to push notifications to subscribers.
 */
public abstract class AbstractNotificationService extends AbstractIdleService implements NotificationService {
  private static final Logger LOG = LoggerFactory.getLogger(InMemoryNotificationService.class);
  protected static final Gson GSON = new GsonBuilder()
    .enableComplexMapKeySerialization()
    .registerTypeAdapter(NotificationFeedInfo.class, new NotificationFeedInfoDeserializer())
    .create();

  private final Multimap<NotificationFeedId, NotificationCaller<?>> subscribers;

  private final DatasetFramework dsFramework;
  private final TransactionSystemClientService transactionSystemClient;
  private final NotificationFeedManager feedManager;

  protected AbstractNotificationService(DatasetFramework dsFramework,
                                        TransactionSystemClientService transactionSystemClient,
                                        NotificationFeedManager feedManager) {
    this.dsFramework = dsFramework;
    this.transactionSystemClient = transactionSystemClient;
    this.feedManager = feedManager;
    this.subscribers = Multimaps.synchronizedMultimap(
      HashMultimap.<NotificationFeedId, NotificationCaller<?>>create());
  }

  @Override
  protected void startUp() throws Exception {
    transactionSystemClient.startAndWait();
  }

  @Override
  protected void shutDown() throws Exception {
    transactionSystemClient.stopAndWait();
  }

  /**
   * Called when a notification is received on a feed, to push it to all the handlers that subscribed to the feed.
   *
   * @param feed {@link NotificationFeedId} of the notification
   * @param notificationJson notification as a json object
   */
  protected void notificationReceived(NotificationFeedId feed, JsonElement notificationJson) {
    LOG.trace("Notification received on feed {}: {}", feed, notificationJson);
    Collection<NotificationCaller<?>> callers = subscribers.get(feed);
    synchronized (subscribers) {
      callers = ImmutableList.copyOf(callers);
    }
    for (NotificationCaller caller : callers) {
      Object notification = GSON.fromJson(notificationJson, caller.getNotificationType());
      caller.received(notification, new BasicNotificationContext(feed.getParent(),
                                                                 dsFramework, transactionSystemClient));
    }
  }

  @Override
  public <N> ListenableFuture<N> publish(NotificationFeedId feed, N notification)
    throws NotificationException {
    return publish(feed, notification, notification.getClass());
  }

  @Override
  public <N> Cancellable subscribe(NotificationFeedId feed, NotificationHandler<N> handler)
    throws NotificationFeedNotFoundException, NotificationFeedException {
    return subscribe(feed, handler, Threads.SAME_THREAD_EXECUTOR);
  }

  @Override
  public <N> Cancellable subscribe(NotificationFeedId feed, NotificationHandler<N> handler, Executor executor)
    throws NotificationFeedNotFoundException, NotificationFeedException {
    // This call will make sure that the feed exists
    feedManager.getFeed(feed);

    NotificationCaller<N> caller = new NotificationCaller<>(feed, handler, executor);
    subscribers.put(feed, caller);
    return caller;
  }

  /**
   * Wrapper around a {@link NotificationHandler}, containing a reference to a {@link NotificationHandler}
   * and a {@link NotificationFeedId}.
   *
   * @param <N> Type of the Notification to handle
   */
  private class NotificationCaller<N> implements NotificationHandler<N>, Cancellable {
    private final NotificationFeedId feed;
    private final NotificationHandler<N> handler;
    private final Executor executor;
    private volatile boolean completed;

    NotificationCaller(NotificationFeedId feed, NotificationHandler<N> handler, Executor executor) {
      this.feed = feed;
      this.handler = handler;
      this.executor = executor;
    }

    @Override
    public Type getNotificationType() {
      return handler.getNotificationType();
    }

    @Override
    public void received(final N notification, final NotificationContext notificationContext) {
      if (completed) {
        return;
      }
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (completed) {
            return;
          }
          try {
            handler.received(notification, notificationContext);
          } catch (Throwable t) {
            LOG.warn("Notification {} on feed {} could not be processed successfully by handler {}",
                     notification, feed, handler, t);
          }
        }
      });
    }

    @Override
    public void cancel() {
      completed = true;
      subscribers.remove(feed, this);
    }
  }
}
