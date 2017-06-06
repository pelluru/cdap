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
import co.cask.cdap.api.retry.RetryableException;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.stream.notification.StreamSizeNotification;
import co.cask.cdap.common.zookeeper.coordination.BalancedAssignmentStrategy;
import co.cask.cdap.common.zookeeper.coordination.PartitionReplica;
import co.cask.cdap.common.zookeeper.coordination.ResourceCoordinator;
import co.cask.cdap.common.zookeeper.coordination.ResourceCoordinatorClient;
import co.cask.cdap.common.zookeeper.coordination.ResourceHandler;
import co.cask.cdap.common.zookeeper.coordination.ResourceModifier;
import co.cask.cdap.common.zookeeper.coordination.ResourceRequirement;
import co.cask.cdap.data.stream.StreamCoordinatorClient;
import co.cask.cdap.data.stream.StreamLeaderListener;
import co.cask.cdap.data.stream.StreamPropertyListener;
import co.cask.cdap.data.stream.service.heartbeat.HeartbeatPublisher;
import co.cask.cdap.data.stream.service.heartbeat.StreamWriterHeartbeat;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.notifications.feeds.NotificationFeedException;
import co.cask.cdap.notifications.feeds.NotificationFeedManager;
import co.cask.cdap.notifications.feeds.NotificationFeedNotFoundException;
import co.cask.cdap.notifications.service.NotificationContext;
import co.cask.cdap.notifications.service.NotificationHandler;
import co.cask.cdap.notifications.service.NotificationService;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NotificationFeedId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.notification.NotificationFeedInfo;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import org.apache.twill.api.ElectionHandler;
import org.apache.twill.api.TwillRunnable;
import org.apache.twill.common.Cancellable;
import org.apache.twill.common.Threads;
import org.apache.twill.discovery.Discoverable;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.apache.twill.internal.zookeeper.LeaderElection;
import org.apache.twill.zookeeper.ZKClient;
import org.apache.twill.zookeeper.ZKClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * Stream service running in a {@link TwillRunnable}. It is responsible for sending {@link StreamWriterHeartbeat}s
 * at a fixed rate, describing the sizes of the stream files on which this service writes data, for each stream.
 */
public class DistributedStreamService extends AbstractStreamService {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedStreamService.class);

  private static final String STREAMS_COORDINATOR = "streams.coordinator";

  private final ZKClient zkClient;
  private final StreamAdmin streamAdmin;
  private final DiscoveryServiceClient discoveryServiceClient;
  private final StreamWriterSizeCollector streamWriterSizeCollector;
  private final HeartbeatPublisher heartbeatPublisher;
  private final StreamMetaStore streamMetaStore;
  private final ResourceCoordinatorClient resourceCoordinatorClient;
  private final NotificationService notificationService;
  private final NotificationFeedManager feedManager;
  private final Set<StreamLeaderListener> leaderListeners;
  private final int instanceId;

  private Cancellable leaderListenerCancellable;

  private final ConcurrentMap<StreamId, StreamSizeAggregator> aggregators;
  private Cancellable heartbeatsSubscription;

  private Supplier<Discoverable> discoverableSupplier;

  private LeaderElection leaderElection;
  private ResourceCoordinator resourceCoordinator;
  private Cancellable coordinationSubscription;
  private ExecutorService heartbeatsSubscriptionExecutor;

  @Inject
  public DistributedStreamService(CConfiguration cConf,
                                  StreamAdmin streamAdmin,
                                  StreamCoordinatorClient streamCoordinatorClient,
                                  StreamFileJanitorService janitorService,
                                  ZKClient zkClient,
                                  DiscoveryServiceClient discoveryServiceClient,
                                  StreamMetaStore streamMetaStore,
                                  Supplier<Discoverable> discoverableSupplier,
                                  StreamWriterSizeCollector streamWriterSizeCollector,
                                  HeartbeatPublisher heartbeatPublisher,
                                  NotificationFeedManager feedManager,
                                  NotificationService notificationService,
                                  MetricStore metricStore) {
    super(streamCoordinatorClient, janitorService, streamWriterSizeCollector, metricStore);
    this.zkClient = zkClient;
    this.streamAdmin = streamAdmin;
    this.notificationService = notificationService;
    this.discoveryServiceClient = discoveryServiceClient;
    this.streamMetaStore = streamMetaStore;
    this.discoverableSupplier = discoverableSupplier;
    this.feedManager = feedManager;
    this.streamWriterSizeCollector = streamWriterSizeCollector;
    this.heartbeatPublisher = heartbeatPublisher;
    this.resourceCoordinatorClient = new ResourceCoordinatorClient(getCoordinatorZKClient());
    this.leaderListeners = Sets.newHashSet();
    this.instanceId = cConf.getInt(Constants.Stream.CONTAINER_INSTANCE_ID);
    this.aggregators = Maps.newConcurrentMap();
  }

  @Override
  protected void initialize() throws Exception {
    LOG.info("Initializing DistributedStreamService.");
    createHeartbeatsFeed();
    heartbeatPublisher.startAndWait();
    resourceCoordinatorClient.startAndWait();
    coordinationSubscription = resourceCoordinatorClient.subscribe(discoverableSupplier.get().getName(),
                                                                   new StreamsLeaderHandler());

    heartbeatsSubscriptionExecutor = Executors.newSingleThreadExecutor(
      Threads.createDaemonThreadFactory("heartbeats-subscription-executor"));
    heartbeatsSubscription = subscribeToHeartbeatsFeed();
    leaderListenerCancellable = addLeaderListener(new StreamLeaderListener() {
      @Override
      public void leaderOf(Set<StreamId> streamIds) {
        aggregate(streamIds);
      }
    });

    performLeaderElection();
    LOG.info("DistributedStreamService initialized.");
  }

  @Override
  protected void doShutdown() throws Exception {
    for (StreamSizeAggregator aggregator : aggregators.values()) {
      aggregator.cancel();
    }

    if (leaderListenerCancellable != null) {
      leaderListenerCancellable.cancel();
    }

    if (heartbeatsSubscription != null) {
      heartbeatsSubscription.cancel();
    }

    if (heartbeatsSubscriptionExecutor != null) {
      heartbeatsSubscriptionExecutor.shutdownNow();
    }

    heartbeatPublisher.stopAndWait();

    if (leaderElection != null) {
      Uninterruptibles.getUninterruptibly(leaderElection.stop(), 5, TimeUnit.SECONDS);
    }

    if (coordinationSubscription != null) {
      coordinationSubscription.cancel();
    }

    if (resourceCoordinatorClient != null) {
      resourceCoordinatorClient.stopAndWait();
    }
  }

  @Override
  protected void runOneIteration() throws Exception {
    LOG.trace("Performing heartbeat publishing in Stream service instance {}", instanceId);
    ImmutableMap.Builder<StreamId, Long> sizes = ImmutableMap.builder();
    Map<StreamId, AtomicLong> streamSizes = streamWriterSizeCollector.getStreamSizes();
    for (Map.Entry<StreamId, AtomicLong> streamSize : streamSizes.entrySet()) {
      sizes.put(streamSize.getKey(), streamSize.getValue().get());
    }
    StreamWriterHeartbeat heartbeat = new StreamWriterHeartbeat(System.currentTimeMillis(), instanceId, sizes.build());
    LOG.trace("Publishing heartbeat {}", heartbeat);
    heartbeatPublisher.sendHeartbeat(heartbeat);
  }

  /**
   * Perform aggregation on the Streams described by the {@code streamIds}, and no other Streams.
   * If aggregation was previously done on other Streams, those must be cancelled.
   *
   * @param streamIds Ids of the streams to perform data sizes aggregation on
   */
  private void aggregate(Set<StreamId> streamIds) {
    Set<StreamId> existingAggregators = Sets.newHashSet(aggregators.keySet());
    for (StreamId streamId : streamIds) {
      if (existingAggregators.remove(streamId)) {
        continue;
      }

      while (true) {
        try {
          if (!streamAdmin.exists(streamId)) {
            break;
          }
          int threshold = streamAdmin.getConfig(streamId).getNotificationThresholdMB();
          long eventsSize = getStreamEventsSize(streamId);
          createSizeAggregator(streamId, eventsSize, threshold);
          LOG.debug("Size of the events ingested in stream {}: {}", streamId, eventsSize);
          break;
        } catch (Exception e) {
          LOG.info("Could not compute sizes of files for stream {}. Retrying in 1 sec.", streamId);
          try {
            TimeUnit.SECONDS.sleep(1);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(ie);
          }
        }
      }
    }

    // Stop aggregating the heartbeats we used to listen to before the call to that method,
    // but don't anymore
    for (StreamId outdatedStream : existingAggregators) {
      // We need to first cancel the aggregator and then remove it from the map of aggregators,
      // to avoid race conditions in createSizeAggregator
      StreamSizeAggregator aggregator = aggregators.get(outdatedStream);
      if (aggregator != null) {
        aggregator.cancel();
      }
      aggregators.remove(outdatedStream);
    }
  }

  /**
   * Create a new aggregator for the {@code streamId}, and add it to the existing map of {@link Cancellable}
   * {@code aggregators}. This method does not cancel previously existing aggregator associated to the
   * {@code streamId}.
   *
   * @param streamId stream Id to create a new aggregator for
   * @param baseCount stream size from which to start aggregating
   * @param threshold notification threshold after which to publish a notification - in MB
   * @return the created {@link StreamSizeAggregator}
   */
  private StreamSizeAggregator createSizeAggregator(StreamId streamId, long baseCount, int threshold) {
    LOG.debug("Creating size aggregator for stream {} with baseCount {} and threshold {}",
              streamId, baseCount, threshold);
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
    newAggregator.init();
    aggregators.put(streamId, newAggregator);
    return newAggregator;
  }

  private ZKClient getCoordinatorZKClient() {
    return ZKClients.namespace(zkClient, Constants.Stream.STREAM_ZK_COORDINATION_NAMESPACE);
  }

  /**
   * Subscribe to the streams heartbeat notification feed. One heartbeat contains data for all existing streams,
   * we filter that to only take into account the streams that this {@link DistributedStreamService} is a leader
   * of.
   *
   * @return a {@link Cancellable} to cancel the subscription
   * @throws NotificationFeedNotFoundException if the heartbeat feed does not exist
   */
  private Cancellable subscribeToHeartbeatsFeed() throws NotificationFeedNotFoundException {
    LOG.debug("Subscribing to stream heartbeats notification feed");
    NotificationFeedId heartbeatsFeed = new NotificationFeedId(
      NamespaceId.SYSTEM.getNamespace(),
      Constants.Notification.Stream.STREAM_INTERNAL_FEED_CATEGORY,
      Constants.Notification.Stream.STREAM_HEARTBEAT_FEED_NAME);

    boolean isRetry = false;
    while (true) {
      try {
        return notificationService.subscribe(heartbeatsFeed, new NotificationHandler<StreamWriterHeartbeat>() {
          @Override
          public Type getNotificationType() {
            return StreamWriterHeartbeat.class;
          }

          @Override
          public void received(StreamWriterHeartbeat heartbeat, NotificationContext notificationContext) {
            LOG.trace("Received heartbeat {}", heartbeat);
            for (Map.Entry<StreamId, Long> entry : heartbeat.getStreamsSizes().entrySet()) {
              StreamSizeAggregator streamSizeAggregator = aggregators.get(entry.getKey());
              if (streamSizeAggregator == null) {
                LOG.trace("Aggregator for stream {} is null", entry.getKey());
                continue;
              }
              streamSizeAggregator.bytesReceived(heartbeat.getInstanceId(), entry.getValue());
            }
          }
        }, heartbeatsSubscriptionExecutor);
      } catch (NotificationFeedException e) {
        if (!isRetry) {
          LOG.warn("Unable to subscribe to HeartbeatsFeed. Will retry until successfully subscribed. " +
                     "Retry failures will be logged at debug level.", e);
        } else {
          LOG.debug("Unable to subscribe to HeartbeatsFeed. Will retry until successfully subscribed. ", e);
        }
        isRetry = true;
        waitBeforeRetryHeartbeatsFeedOperation();
      }
    }
  }

  /**
   * This method is called every time the Stream handler in which this {@link DistributedStreamService}
   * runs becomes the leader of a set of streams. Prior to this call, the Stream handler might
   * already have been the leader of some of those streams.
   *
   * @param listener {@link StreamLeaderListener} called when this Stream handler becomes leader
   *                 of a collection of streams
   * @return A {@link Cancellable} to cancel the watch
   */
  private Cancellable addLeaderListener(final StreamLeaderListener listener) {
    synchronized (this) {
      leaderListeners.add(listener);
    }
    return new Cancellable() {
      @Override
      public void cancel() {
        synchronized (DistributedStreamService.this) {
          leaderListeners.remove(listener);
        }
      }
    };
  }

  /**
   * Create Notification feed for stream's heartbeats, if it does not already exist.
   */
  private void createHeartbeatsFeed() throws NotificationFeedException {
    NotificationFeedInfo streamHeartbeatsFeed = new NotificationFeedInfo(
      NamespaceId.SYSTEM.getEntityName(),
      Constants.Notification.Stream.STREAM_INTERNAL_FEED_CATEGORY,
      Constants.Notification.Stream.STREAM_HEARTBEAT_FEED_NAME,
      "Stream heartbeats feed.");

    LOG.debug("Ensuring Stream HeartbeatsFeed exists.");
    boolean isRetry = false;
    while (true) {
      try {
        feedManager.getFeed(streamHeartbeatsFeed);
        LOG.debug("Stream HeartbeatsFeed exists.");
        return;
      } catch (NotificationFeedNotFoundException notFoundException) {
        if (!isRetry) {
          LOG.debug("Creating Stream HeartbeatsFeed.");
        }
        feedManager.createFeed(streamHeartbeatsFeed);
        LOG.info("Stream HeartbeatsFeed created.");
        return;
      } catch (NotificationFeedException | RetryableException e) {
        if (!isRetry) {
          LOG.warn("Could not ensure existence of HeartbeatsFeed. Will retry until successful. " +
                     "Retry failures will be logged at debug level.", e);
        } else {
          LOG.debug("Could not ensure existence of HeartbeatsFeed. Will retry until successful.", e);
        }
        isRetry = true;
        waitBeforeRetryHeartbeatsFeedOperation();
      }
    }
  }

  private void waitBeforeRetryHeartbeatsFeedOperation() {
    // Most probably, the dataset service is not up. We retry
    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw Throwables.propagate(ie);
    }
  }

  /**
   * Elect one leader among the {@link DistributedStreamService}s running in different Twill runnables.
   */
  private void performLeaderElection() {
    // Start the resource coordinator that will map Streams to Stream handlers
    leaderElection = new LeaderElection(
      // TODO: Should unify this leader election with DistributedStreamFileJanitorService
      zkClient, "/election/" + STREAMS_COORDINATOR, new ElectionHandler() {
      @Override
      public void leader() {
        LOG.info("Became Stream handler leader. Starting resource coordinator.");
        resourceCoordinator = new ResourceCoordinator(getCoordinatorZKClient(), discoveryServiceClient,
                                                      new BalancedAssignmentStrategy());
        resourceCoordinator.startAndWait();
        updateRequirement();
      }

      @Override
      public void follower() {
        LOG.info("Became Stream handler follower.");
        if (resourceCoordinator != null) {
          resourceCoordinator.stopAndWait();
        }
      }
    });
    leaderElection.start();
  }

  /**
   * Updates stream resource requirement. It will retry if failed to do so.
   */
  private void updateRequirement() {
    final ResourceModifier modifier = createRequirementModifier();
    Futures.addCallback(resourceCoordinatorClient.modifyRequirement(Constants.Service.STREAMS, modifier),
                        new FutureCallback<ResourceRequirement>() {
      @Override
      public void onSuccess(ResourceRequirement result) {
        // No-op
        LOG.info("Stream resource requirement updated to {}", result);
      }

      @Override
      public void onFailure(Throwable t) {
        LOG.warn("Failed to update stream resource requirement: {}", t.getMessage());
        LOG.debug("Failed to update stream resource requirement.", t);
        if (isRunning()) {
          final FutureCallback<ResourceRequirement> callback = this;
          // Retry in 2 seconds. Shouldn't sleep in this callback thread. Should start a new thread for the retry.
          Thread retryThread = new Thread("stream-resource-update") {
            @Override
            public void run() {
              try {
                TimeUnit.SECONDS.sleep(2);
                LOG.info("Retrying update stream resource requirement");
                Futures.addCallback(resourceCoordinatorClient.modifyRequirement(Constants.Service.STREAMS, modifier),
                                    callback);
              } catch (InterruptedException e) {
                LOG.warn("Stream resource retry thread interrupted", e);
              }
            }
          };
          retryThread.setDaemon(true);
          retryThread.start();
        }
      }
    });
  }

  /**
   * Creates a {@link ResourceModifier} that updates stream resource requirement by consulting stream meta store.
   */
  private ResourceModifier createRequirementModifier() {
    return new ResourceModifier() {
      @Nullable
      @Override
      public ResourceRequirement apply(@Nullable ResourceRequirement existingRequirement) {
        try {
          // Create one requirement for the resource coordinator for all the streams.
          // One stream is identified by one partition
          ResourceRequirement.Builder builder = ResourceRequirement.builder(Constants.Service.STREAMS);
          for (Map.Entry<NamespaceId, StreamSpecification> streamSpecEntry : streamMetaStore.listStreams().entries()) {
            StreamId streamId = streamSpecEntry.getKey().stream(streamSpecEntry.getValue().getName());
            LOG.debug("Adding {} stream as a resource to the coordinator to manager streams leaders.", streamId);
            builder.addPartition(new ResourceRequirement.Partition(streamId.toString(), 1));
          }
          return builder.build();
        } catch (Throwable e) {
          LOG.warn("Could not create requirement for coordinator in Stream handler leader: " + e.getMessage());
          LOG.debug("Could not create requirement for coordinator in Stream handler leader", e);
          throw Throwables.propagate(e);
        }
      }
    };
  }

  /**
   * Call all the listeners that are interested in knowing that this Stream writer is the leader of a set of Streams.
   *
   * @param streamIds set of Streams that this coordinator is the leader of
   */
  private void invokeLeaderListeners(Set<StreamId> streamIds) {
    LOG.debug("Stream writer is the leader of streams: {}", streamIds);
    Set<StreamLeaderListener> listeners;
    synchronized (this) {
      listeners = ImmutableSet.copyOf(leaderListeners);
    }
    for (StreamLeaderListener listener : listeners) {
      listener.leaderOf(streamIds);
    }
  }

  /**
   * Class that defines the behavior of a leader of a collection of Streams.
   */
  private final class StreamsLeaderHandler extends ResourceHandler {

    protected StreamsLeaderHandler() {
      super(discoverableSupplier.get());
    }

    @Override
    public void onChange(Collection<PartitionReplica> partitionReplicas) {
      LOG.info("Stream leader requirement has changed to {}", partitionReplicas);
      Set<StreamId> streamIds =
        ImmutableSet.copyOf(Iterables.transform(partitionReplicas, new Function<PartitionReplica, StreamId>() {
          @Nullable
          @Override
          public StreamId apply(@Nullable PartitionReplica input) {
            return input != null ? StreamId.fromString(input.getName()) : null;
          }
        }));
      invokeLeaderListeners(ImmutableSet.copyOf(streamIds));
    }

    @Override
    public void finished(Throwable failureCause) {
      if (failureCause != null) {
        LOG.error("Finished with failure for Stream handler instance {}", discoverableSupplier.get().getName(),
                  failureCause);
      }
    }
  }

  /**
   * Aggregate the sizes of all stream writers. A notification is published if the aggregated
   * size is higher than a threshold.
   */
  private final class StreamSizeAggregator implements Cancellable {

    private final Map<Integer, Long> streamWriterSizes;
    private final NotificationFeedId streamFeed;
    private final AtomicLong streamBaseCount;
    private final long streamInitSize;
    private final AtomicInteger streamThresholdMB;
    private final Cancellable cancellable;
    private final StreamId streamId;

    protected StreamSizeAggregator(StreamId streamId, long baseCount, int streamThresholdMB, Cancellable cancellable) {
      this.streamWriterSizes = Maps.newHashMap();
      this.streamBaseCount = new AtomicLong(baseCount);
      this.streamInitSize = baseCount;
      this.streamThresholdMB = new AtomicInteger(streamThresholdMB);
      this.cancellable = cancellable;
      this.streamId = streamId;
      this.streamFeed = new NotificationFeedId(streamId.getNamespace(),
                                               Constants.Notification.Stream.STREAM_FEED_CATEGORY,
                                               String.format("%sSize", streamId.getEntityName()));
    }

    /**
     * Initialize this {@link StreamSizeAggregator}.
     */
    public void init() {
      // Publish an initialization notification
      publishNotification(streamInitSize);
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
      LOG.debug("Updating threshold of size aggregator for stream {}: {}MB", streamId, newThreshold);
      streamThresholdMB.set(newThreshold);
    }

    /**
     * Notify this aggregator that a certain number of bytes have been received from the stream writer with instance
     * {@code instanceId}.
     *
     * @param instanceId id of the stream writer from which we received some bytes
     * @param nbBytes number of bytes of data received
     */
    public void bytesReceived(int instanceId, long nbBytes) {
      LOG.trace("Bytes received from instanceId {}: {}B", instanceId, nbBytes);
      streamWriterSizes.put(instanceId, nbBytes);
      checkSendNotification();
    }

    /**
     * Check if the current size of data is enough to trigger a notification.
     */
    private void checkSendNotification() {
      long sum = streamInitSize;
      for (Long size : streamWriterSizes.values()) {
        sum += size;
      }

      LOG.trace("Check notification publishing: sum is {}, baseCount is {}", sum, streamBaseCount);
      if (sum - streamBaseCount.get() > toBytes(streamThresholdMB.get())) {
        try {
          publishNotification(sum);
        } finally {
          streamBaseCount.set(sum);
        }
      }
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
        LOG.warn("Could not publish notification on feed {}", streamFeed, t);
      }
    }
  }
}
