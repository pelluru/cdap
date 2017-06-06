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

package co.cask.cdap.data.stream.service;

import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.metrics.MetricStore;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.stream.notification.StreamSizeNotification;
import co.cask.cdap.data.stream.StreamCoordinatorClient;
import co.cask.cdap.data.stream.StreamPropertyListener;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.notifications.feeds.NotificationFeedException;
import co.cask.cdap.notifications.service.NotificationService;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NotificationFeedId;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.twill.common.Cancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stream service running in local mode.
 */
public class LocalStreamService extends AbstractStreamService {
  private static final Logger LOG = LoggerFactory.getLogger(LocalStreamService.class);

  private final NotificationService notificationService;
  private final StreamAdmin streamAdmin;
  private final StreamWriterSizeCollector streamWriterSizeCollector;
  private final StreamMetaStore streamMetaStore;
  private final ConcurrentMap<StreamId, StreamSizeAggregator> aggregators;

  @Inject
  public LocalStreamService(StreamCoordinatorClient streamCoordinatorClient,
                            StreamFileJanitorService janitorService,
                            StreamMetaStore streamMetaStore,
                            StreamAdmin streamAdmin,
                            StreamWriterSizeCollector streamWriterSizeCollector,
                            NotificationService notificationService,
                            MetricStore metricStore) {
    super(streamCoordinatorClient, janitorService, streamWriterSizeCollector, metricStore);
    this.streamAdmin = streamAdmin;
    this.streamMetaStore = streamMetaStore;
    this.streamWriterSizeCollector = streamWriterSizeCollector;
    this.notificationService = notificationService;
    this.aggregators = Maps.newConcurrentMap();
  }

  @Override
  protected void initialize() throws Exception {
    for (Map.Entry<NamespaceId, StreamSpecification> streamSpecEntry : streamMetaStore.listStreams().entries()) {
      StreamId streamId = streamSpecEntry.getKey().stream(streamSpecEntry.getValue().getName());
      StreamConfig config;
      try {
        config = streamAdmin.getConfig(streamId);
      } catch (FileNotFoundException e) {
        // TODO: this kind of inconsistency should not happen. [CDAP-5722]
        LOG.warn("Inconsistent stream state: Stream '{}' exists in meta store " +
                   "but its configuration file does not exist", streamId);
        continue;
      } catch (Exception e) {
        LOG.warn("Inconsistent stream state: Stream '{}' exists in meta store " +
                   "but its configuration cannot be read:", streamId, e);
        continue;
      }
      long eventsSizes = getStreamEventsSize(streamId);
      createSizeAggregator(streamId, eventsSizes, config.getNotificationThresholdMB());
    }
  }

  @Override
  protected void doShutdown() throws Exception {
    for (StreamSizeAggregator streamSizeAggregator : aggregators.values()) {
      streamSizeAggregator.cancel();
    }
  }

  @Override
  protected void runOneIteration() throws Exception {
    // Get stream size - which will be the entire size - and send a notification if the size is big enough
    for (Map.Entry<NamespaceId, StreamSpecification> streamSpecEntry : streamMetaStore.listStreams().entries()) {
      StreamId streamId = streamSpecEntry.getKey().stream(streamSpecEntry.getValue().getName());
      StreamSizeAggregator streamSizeAggregator = aggregators.get(streamId);
      try {
        if (streamSizeAggregator == null) {
          // First time that we see this Stream here
          StreamConfig config;
          try {
            config = streamAdmin.getConfig(streamId);
          } catch (FileNotFoundException e) {
            // this is a stream that has no configuration: ignore it to avoid flooding the logs with exceptions
            continue;
          }
          streamSizeAggregator = createSizeAggregator(streamId, 0, config.getNotificationThresholdMB());
        }
        streamSizeAggregator.checkAggregatedSize();
      } catch (Exception e) {
        // Need to catch and not to propagate the exception, otherwise this scheduled service will be terminated
        // Just log the exception here as the next run iteration should have the problem fixed
        LOG.warn("Exception in aggregating stream size for {}", streamId, e);
      }
    }
  }

  /**
   * Create a new aggregator for the {@code streamId}, and add it to the existing map of {@link Cancellable}
   * {@code aggregators}. This method does not cancel previously existing aggregator associated to the
   * {@code streamId}.
   *
   * @param streamId stream name to create a new aggregator for
   * @param baseCount stream size from which to start aggregating
   * @param threshold notification threshold after which to publish a notification - in MB
   * @return the created {@link StreamSizeAggregator}
   */
  private StreamSizeAggregator createSizeAggregator(StreamId streamId, long baseCount, int threshold) {

    // Handle threshold changes
    final Cancellable thresholdSubscription =
      getStreamCoordinatorClient().addListener(streamId, new StreamPropertyListener() {
        @Override
        public void thresholdChanged(StreamId streamId, int threshold) {
          StreamSizeAggregator aggregator = aggregators.get(streamId);
          while (aggregator == null) {
            Thread.yield();
            aggregator = aggregators.get(streamId);
          }
          aggregator.setStreamThresholdMB(threshold);
        }
      });

    StreamSizeAggregator newAggregator = new StreamSizeAggregator(streamId, baseCount, threshold,
                                                                  thresholdSubscription);
    aggregators.put(streamId, newAggregator);
    return newAggregator;
  }

  /**
   * Aggregate the sizes of all stream writers. A notification is published if the aggregated
   * size is higher than a threshold.
   */
  private final class StreamSizeAggregator implements Cancellable {
    private final long streamInitSize;
    private final NotificationFeedId streamFeed;
    private final StreamId streamId;
    private final AtomicLong streamBaseCount;
    private final AtomicInteger streamThresholdMB;
    private final Cancellable cancellable;
    private boolean published;

    protected StreamSizeAggregator(StreamId streamId, long baseCount, int streamThresholdMB, Cancellable cancellable) {
      this.streamId = streamId;
      this.streamInitSize = baseCount;
      this.streamBaseCount = new AtomicLong(baseCount);
      this.cancellable = cancellable;
      this.streamFeed = new NotificationFeedId(
        streamId.getNamespace(),
        Constants.Notification.Stream.STREAM_FEED_CATEGORY,
        String.format("%sSize", streamId.getEntityName()));
      this.streamThresholdMB = new AtomicInteger(streamThresholdMB);
    }

    @Override
    public void cancel() {
      cancellable.cancel();
    }

    /**
     * Set the notification threshold for the stream that this {@link StreamSizeAggregator} is linked to.
     *
     * @param newThreshold new notification threshold, in megabytes
     */
    public void setStreamThresholdMB(int newThreshold) {
      streamThresholdMB.set(newThreshold);
    }

    /**
     * Check that the aggregated size of the heartbeats received by all Stream writers is higher than some threshold.
     * If it is, we publish a notification.
     */
    public void checkAggregatedSize() {
      long sum = streamInitSize + streamWriterSizeCollector.getTotalCollected(streamId);
      if (!published || sum - streamBaseCount.get() > toBytes(streamThresholdMB.get())) {
        try {
          publishNotification(sum);
        } finally {
          streamBaseCount.set(sum);
        }
      }
      published = true;
    }

    private long toBytes(int mb) {
      return ((long) mb) * 1024 * 1024;
    }

    private void publishNotification(long absoluteSize) {
      try {
        notificationService.publish(streamFeed, new StreamSizeNotification(System.currentTimeMillis(), absoluteSize))
          .get();
      } catch (NotificationFeedException e) {
        LOG.warn("Error with notification feed {}", streamFeed, e);
      } catch (Throwable t) {
        LOG.debug("Could not publish notification on feed {}", streamFeed, t);
      }
    }
  }
}
