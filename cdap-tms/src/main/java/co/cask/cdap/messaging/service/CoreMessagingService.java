/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.messaging.service;

import co.cask.cdap.api.messaging.TopicAlreadyExistsException;
import co.cask.cdap.api.messaging.TopicNotFoundException;
import co.cask.cdap.api.metrics.MetricsCollectionService;
import co.cask.cdap.api.metrics.MetricsContext;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.utils.TimeProvider;
import co.cask.cdap.messaging.MessageFetcher;
import co.cask.cdap.messaging.MessagingService;
import co.cask.cdap.messaging.MessagingServiceUtils;
import co.cask.cdap.messaging.MessagingUtils;
import co.cask.cdap.messaging.RollbackDetail;
import co.cask.cdap.messaging.StoreRequest;
import co.cask.cdap.messaging.TopicMetadata;
import co.cask.cdap.messaging.store.MessageTable;
import co.cask.cdap.messaging.store.MetadataTable;
import co.cask.cdap.messaging.store.PayloadTable;
import co.cask.cdap.messaging.store.TableFactory;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.TopicId;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import org.apache.tephra.TxConstants;
import org.apache.tephra.util.TxUtils;
import org.apache.twill.common.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The core implementation of {@link MessagingService}. It interacts with {@link MessageTable} and {@link PayloadTable}
 * directly to provide the messaging functionality.
 */
@ThreadSafe
public class CoreMessagingService extends AbstractIdleService implements MessagingService {

  private static final Logger LOG = LoggerFactory.getLogger(CoreMessagingService.class);

  private final CConfiguration cConf;
  private final TableFactory tableFactory;
  private final LoadingCache<TopicId, TopicMetadata> topicCache;
  private final LoadingCache<TopicId, ConcurrentMessageWriter> messageTableWriterCache;
  private final LoadingCache<TopicId, ConcurrentMessageWriter> payloadTableWriterCache;
  private final TimeProvider timeProvider;
  private final MetricsCollectionService metricsCollectionService;
  private final long txMaxLifeTimeInMillis;

  @Inject
  CoreMessagingService(CConfiguration cConf, TableFactory tableFactory,
                       MetricsCollectionService metricsCollectionService) {
    this(cConf, tableFactory, TimeProvider.SYSTEM_TIME, metricsCollectionService);
  }

  @VisibleForTesting
  CoreMessagingService(CConfiguration cConf, TableFactory tableFactory,
                       TimeProvider timeProvider, MetricsCollectionService metricsCollectionService) {
    this.cConf = cConf;
    this.tableFactory = tableFactory;
    this.topicCache = createTopicCache();
    this.messageTableWriterCache = createTableWriterCache(true, cConf);
    this.payloadTableWriterCache = createTableWriterCache(false, cConf);
    this.timeProvider = timeProvider;

    // Due to circular dependency in our class hierarchy (which is bad), we cannot use metricsCollectionService
    // to construct metricsContext in here. The circular dependency is

    // metrics collection ->
    //   metrics store ->
    //    lineage dataset framework ->
    //      audit publisher ->
    //        messaging service ->
    //          "metrics collection"
    this.metricsCollectionService = metricsCollectionService;
    this.txMaxLifeTimeInMillis = TimeUnit.SECONDS.toMillis(cConf.getLong(TxConstants.Manager.CFG_TX_MAX_LIFETIME,
                                                                         TxConstants.Manager.DEFAULT_TX_MAX_LIFETIME));
  }

  @Override
  public void createTopic(TopicMetadata topicMetadata) throws TopicAlreadyExistsException, IOException {
    try (MetadataTable metadataTable = createMetadataTable()) {
      Map<String, String> properties = createDefaultProperties();
      properties.putAll(topicMetadata.getProperties());
      metadataTable.createTopic(new TopicMetadata(topicMetadata.getTopicId(), properties, true));
    }
  }

  @Override
  public void updateTopic(TopicMetadata topicMetadata) throws TopicNotFoundException, IOException {
    try (MetadataTable metadataTable = createMetadataTable()) {
      Map<String, String> properties = createDefaultProperties();
      properties.putAll(topicMetadata.getProperties());
      metadataTable.updateTopic(new TopicMetadata(topicMetadata.getTopicId(), properties, true));
      topicCache.invalidate(topicMetadata.getTopicId());
    }
  }

  @Override
  public void deleteTopic(TopicId topicId) throws TopicNotFoundException, IOException {
    try (MetadataTable metadataTable = createMetadataTable()) {
      metadataTable.deleteTopic(topicId);
      topicCache.invalidate(topicId);
      messageTableWriterCache.invalidate(topicId);
      payloadTableWriterCache.invalidate(topicId);
    }
  }

  @Override
  public TopicMetadata getTopic(TopicId topicId) throws TopicNotFoundException, IOException {
    try {
      return topicCache.get(topicId);
    } catch (ExecutionException e) {
      Throwable cause = Objects.firstNonNull(e.getCause(), e);
      Throwables.propagateIfPossible(cause, TopicNotFoundException.class, IOException.class);
      throw Throwables.propagate(e.getCause());
    }
  }

  @Override
  public List<TopicId> listTopics(NamespaceId namespaceId) throws IOException {
    try (MetadataTable metadataTable = createMetadataTable()) {
      return metadataTable.listTopics(namespaceId);
    }
  }

  @Override
  public MessageFetcher prepareFetch(final TopicId topicId) throws TopicNotFoundException, IOException {
    final TopicMetadata metadata = getTopic(topicId);
    return new CoreMessageFetcher(metadata, new TableProvider<MessageTable>() {
      @Override
      public MessageTable get() throws IOException {
        return createMessageTable(metadata);
      }
    }, new TableProvider<PayloadTable>() {
      @Override
      public PayloadTable get() throws IOException {
        return createPayloadTable(metadata);
      }
    });
  }

  @Nullable
  @Override
  public RollbackDetail publish(StoreRequest request) throws TopicNotFoundException, IOException {
    try {
      TopicMetadata metadata = topicCache.get(request.getTopicId());
      if (request.isTransactional()) {
        ensureValidTxLifetime(request.getTransactionWritePointer());
      }
      return messageTableWriterCache.get(request.getTopicId()).persist(request, metadata);
    } catch (ExecutionException e) {
      Throwable cause = Objects.firstNonNull(e.getCause(), e);
      Throwables.propagateIfPossible(cause, TopicNotFoundException.class, IOException.class);
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void storePayload(StoreRequest request) throws TopicNotFoundException, IOException {
    try {
      TopicMetadata metadata = topicCache.get(request.getTopicId());
      payloadTableWriterCache.get(request.getTopicId()).persist(request, metadata);
    } catch (ExecutionException e) {
      Throwable cause = Objects.firstNonNull(e.getCause(), e);
      Throwables.propagateIfPossible(cause, TopicNotFoundException.class, IOException.class);
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void rollback(TopicId topicId, RollbackDetail rollbackDetail) throws TopicNotFoundException, IOException {
    TopicMetadata metadata = getTopic(topicId);
    Exception failure = null;
    try (MessageTable messageTable = createMessageTable(metadata)) {
      messageTable.rollback(metadata, rollbackDetail);
    } catch (Exception e) {
      failure = e;
    }

    // Throw if there is any failure in rollback.
    if (failure != null) {
      Throwables.propagateIfPossible(failure, TopicNotFoundException.class, IOException.class);
      throw Throwables.propagate(failure);
    }
  }

  @Override
  protected void startUp() throws Exception {
    Queue<TopicId> asyncCreationTopics = new LinkedList<>();

    Set<TopicId> systemTopics = MessagingServiceUtils.getSystemTopics(cConf, true);
    for (TopicId topic : systemTopics) {
      createSystemTopic(topic, asyncCreationTopics);
    }

    if (!asyncCreationTopics.isEmpty()) {
      startAsyncTopicCreation(asyncCreationTopics, 5, TimeUnit.SECONDS);
    }

    LOG.info("Core Messaging Service started");
  }

  /**
   * Creates the given topic if it is not yet created. Adds a topic to the {@code creationFailureTopics}
   * if creation fails.
   */
  private void createSystemTopic(TopicId topicId, Queue<TopicId> creationFailureTopics) {
    try {
      createTopicIfNotExists(topicId);
    } catch (Exception e) {
      // If failed, add it to a list so that the retry will happen asynchronously
      LOG.warn("Topic {} creation failed with exception {}. Will retry.", topicId, e.getMessage());
      LOG.debug("Topic {} creation failure stacktrace", topicId, e);
      creationFailureTopics.add(topicId);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    messageTableWriterCache.invalidateAll();
    messageTableWriterCache.invalidateAll();
    payloadTableWriterCache.invalidateAll();
    LOG.info("Core Messaging Service stopped");
  }

  private void ensureValidTxLifetime(long transactionWritePointer) throws IOException {
    long txTimestamp = TxUtils.getTimestamp(transactionWritePointer);
    boolean validLifetime = (txTimestamp + txMaxLifeTimeInMillis) > System.currentTimeMillis();
    if (!validLifetime) {
      throw new IOException(String.format("Transaction %s has exceeded max lifetime %s ms",
                                          transactionWritePointer, txMaxLifeTimeInMillis));
    }
  }

  /**
   * Starts a thread to create the give list of topics. The thread will keep trying the creation until
   * all of the given topics are created.
   */
  private void startAsyncTopicCreation(final Queue<TopicId> topics, final long delay, final TimeUnit unit) {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
      Threads.createDaemonThreadFactory("async-topic-creation"));

    executor.schedule(new Runnable() {
      @Override
      public void run() {
        Iterator<TopicId> iterator = topics.iterator();
        while (iterator.hasNext()) {
          TopicId topicId = iterator.next();
          try {
            createTopicIfNotExists(topicId);
            // Successfully created, remove it from the async list
            iterator.remove();
          } catch (Exception e) {
            LOG.warn("Topic {} creation failed with exception {}. Will retry.", topicId, e.getMessage());
            LOG.debug("Topic {} creation failure stacktrace", topicId, e);
          }
        }

        if (!topics.isEmpty()) {
          executor.schedule(this, delay, unit);
        }
      }
    }, delay, unit);
  }

  /**
   * Creates the given topic if it is not yet created.
   */
  private void createTopicIfNotExists(TopicId topicId) throws IOException {
    try {
      createTopic(new TopicMetadata(topicId));
      LOG.info("System topic created: {}", topicId);
    } catch (TopicAlreadyExistsException e) {
      // OK for the topic already created. Just log a debug as it happens on every restart.
      LOG.debug("System topic already exists: {}", topicId);
    }
  }

  /**
   * Creates a loading cache for {@link TopicMetadata}.
   */
  private LoadingCache<TopicId, TopicMetadata> createTopicCache() {
    return CacheBuilder.newBuilder().build(new CacheLoader<TopicId, TopicMetadata>() {
      @Override
      public TopicMetadata load(TopicId topicId) throws Exception {
        try (MetadataTable metadataTable = createMetadataTable()) {
          return metadataTable.getMetadata(topicId);
        }
      }
    });
  }

  /**
   * Creates a {@link LoadingCache} for {@link ConcurrentMessageWriter}
   * for writing to {@link MessageTable} or {@link PayloadTable}.
   *
   * @param messageTable {@code true} for building a cache for the {@link MessageTable};
   *                     {@code false} for the {@link PayloadTable}
   * @param cConf the system configuration
   * @return a {@link LoadingCache} for
   */
  private LoadingCache<TopicId, ConcurrentMessageWriter> createTableWriterCache(final boolean messageTable,
                                                                                final CConfiguration cConf) {
    long expireSecs = cConf.getLong(Constants.MessagingSystem.TABLE_CACHE_EXPIRATION_SECONDS);

    return CacheBuilder.newBuilder()
      .expireAfterAccess(expireSecs, TimeUnit.SECONDS)
      .removalListener(new RemovalListener<TopicId, ConcurrentMessageWriter>() {
        @Override
        public void onRemoval(RemovalNotification<TopicId, ConcurrentMessageWriter> notification) {
          ConcurrentMessageWriter writer = notification.getValue();
          if (writer != null) {
            try {
              writer.close();
            } catch (IOException e) {
              LOG.warn("Exception raised when closing message writer for topic {}", notification.getKey(), e);
            }
          }
        }
      })
      .build(new CacheLoader<TopicId, ConcurrentMessageWriter>() {
        @Override
        public ConcurrentMessageWriter load(TopicId topicId) throws Exception {
          TopicMetadata metadata = getTopic(topicId);
          StoreRequestWriter<?> messagesWriter = messageTable
            ? new MessageTableStoreRequestWriter(createMessageTable(metadata), timeProvider)
            : new PayloadTableStoreRequestWriter(createPayloadTable(metadata), timeProvider);

          MetricsContext metricsContext = metricsCollectionService.getContext(ImmutableMap.of(
            Constants.Metrics.Tag.COMPONENT, Constants.Service.MESSAGING_SERVICE,
            Constants.Metrics.Tag.INSTANCE_ID, cConf.get(Constants.MessagingSystem.CONTAINER_INSTANCE_ID, "0"),
            Constants.Metrics.Tag.NAMESPACE, topicId.getNamespace(),
            Constants.Metrics.Tag.TABLE, messageTable ? "message" : "payload"
          ));

          return new ConcurrentMessageWriter(messagesWriter, metricsContext);
        }
      });
  }

  /**
   * Creates a new instance of {@link MetadataTable}.
   */
  private MetadataTable createMetadataTable() throws IOException {
    return tableFactory.createMetadataTable(cConf.get(Constants.MessagingSystem.METADATA_TABLE_NAME));
  }

  private MessageTable createMessageTable(@SuppressWarnings("unused") TopicMetadata topicMetadata) throws IOException {
    // Currently we don't support customizable table name yet, hence always get it from cConf.
    // Later on it can be done by topic properties, with impersonation setting as well.
    return tableFactory.createMessageTable(cConf.get(Constants.MessagingSystem.MESSAGE_TABLE_NAME));
  }

  private PayloadTable createPayloadTable(@SuppressWarnings("unused") TopicMetadata topicMetadata) throws IOException {
    // Currently we don't support customizable table name yet, hence always get it from cConf.
    // Later on it can be done by topic properties, with impersonation setting as well.
    return tableFactory.createPayloadTable(cConf.get(Constants.MessagingSystem.PAYLOAD_TABLE_NAME));
  }

  /**
   * Creates default topic properties based on {@link CConfiguration}.
   */
  private Map<String, String> createDefaultProperties() {
    Map<String, String> properties = new HashMap<>();

    // Default properties
    properties.put(TopicMetadata.TTL_KEY, cConf.get(Constants.MessagingSystem.TOPIC_DEFAULT_TTL_SECONDS));
    properties.put(TopicMetadata.GENERATION_KEY, MessagingUtils.Constants.DEFAULT_GENERATION);
    return properties;
  }
}
