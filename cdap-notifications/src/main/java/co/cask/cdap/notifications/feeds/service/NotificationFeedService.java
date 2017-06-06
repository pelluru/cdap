/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.notifications.feeds.service;

import co.cask.cdap.notifications.feeds.NotificationFeedException;
import co.cask.cdap.notifications.feeds.NotificationFeedManager;
import co.cask.cdap.notifications.feeds.NotificationFeedNotFoundException;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NotificationFeedId;
import co.cask.cdap.proto.notification.NotificationFeedInfo;
import com.google.inject.Inject;

import java.util.List;

/**
 * Service side of the {@link NotificationFeedManager}.
 */
public class NotificationFeedService implements NotificationFeedManager {
  // TODO once [CDAP-903] is completed, then creating a namespace will create a store in that namespace,
  // and any operation on that store will only be possible if it exists, hence there will be no need
  // to check for the existence of a namespace.
  // If we don't create one store per namespace, how do we check for the existence of a namespace
  // when creating a feed?
  private final NotificationFeedStore store;

  @Inject
  public NotificationFeedService(NotificationFeedStore store) {
    this.store = store;
  }

  @Override
  public boolean createFeed(NotificationFeedInfo feed) throws NotificationFeedException {
    return store.createNotificationFeed(feed) == null;
  }

  @Override
  public void deleteFeed(NotificationFeedId feed) throws NotificationFeedNotFoundException {
    if (store.deleteNotificationFeed(feed) == null) {
      throw new NotificationFeedNotFoundException(feed);
    }
  }

  @Override
  public NotificationFeedInfo getFeed(NotificationFeedId feed) throws NotificationFeedNotFoundException {
    NotificationFeedInfo f = store.getNotificationFeed(feed);
    if (f == null) {
      throw new NotificationFeedNotFoundException(feed);
    }
    return f;
  }

  @Override
  public List<NotificationFeedInfo> listFeeds(NamespaceId namespace) throws NotificationFeedException {
    return store.listNotificationFeeds(namespace);
  }
}
