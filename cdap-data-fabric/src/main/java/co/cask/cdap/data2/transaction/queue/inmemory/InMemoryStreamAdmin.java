/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

package co.cask.cdap.data2.transaction.queue.inmemory;

import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.common.StreamNotFoundException;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.queue.QueueName;
import co.cask.cdap.data.stream.service.StreamMetaStore;
import co.cask.cdap.data.view.ViewAdmin;
import co.cask.cdap.data2.audit.AuditPublisher;
import co.cask.cdap.data2.audit.AuditPublishers;
import co.cask.cdap.data2.metadata.lineage.AccessType;
import co.cask.cdap.data2.metadata.store.MetadataStore;
import co.cask.cdap.data2.metadata.writer.LineageWriter;
import co.cask.cdap.data2.registry.UsageRegistry;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.proto.StreamProperties;
import co.cask.cdap.proto.ViewSpecification;
import co.cask.cdap.proto.audit.AuditPayload;
import co.cask.cdap.proto.audit.AuditType;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramRunId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.id.StreamViewId;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * admin for queues in memory.
 */
@Singleton
public class InMemoryStreamAdmin extends InMemoryQueueAdmin implements StreamAdmin {
  private final StreamMetaStore streamMetaStore;
  private final UsageRegistry usageRegistry;
  private final LineageWriter lineageWriter;
  private final MetadataStore metadataStore;
  private final ViewAdmin viewAdmin;

  private AuditPublisher auditPublisher;

  @Inject
  public InMemoryStreamAdmin(InMemoryQueueService queueService,
                             UsageRegistry usageRegistry,
                             LineageWriter lineageWriter,
                             StreamMetaStore streamMetaStore,
                             MetadataStore metadataStore,
                             ViewAdmin viewAdmin) {
    super(queueService);
    this.usageRegistry = usageRegistry;
    this.streamMetaStore = streamMetaStore;
    this.lineageWriter = lineageWriter;
    this.metadataStore = metadataStore;
    this.viewAdmin = viewAdmin;
  }

  @SuppressWarnings("unused")
  @Inject(optional = true)
  public void setAuditPublisher(AuditPublisher auditPublisher) {
    this.auditPublisher = auditPublisher;
  }

  @Override
  public void dropAllInNamespace(NamespaceId namespace) throws Exception {
    queueService.resetStreamsWithPrefix(QueueName.prefixForNamedspacedStream(namespace.getNamespace()));
    for (StreamSpecification spec : streamMetaStore.listStreams(namespace)) {
      // Remove metadata for the stream
      StreamId stream = namespace.stream(spec.getName());
      metadataStore.removeMetadata(stream);
      streamMetaStore.removeStream(stream);
    }
  }

  @Override
  public void configureInstances(StreamId streamId, long groupId, int instances) throws Exception {
    // No-op
  }

  @Override
  public void configureGroups(StreamId streamId, Map<Long, Integer> groupInfo) throws Exception {
    // No-op
  }

  @Override
  public List<StreamSpecification> listStreams(NamespaceId namespaceId) throws Exception {
    return streamMetaStore.listStreams(namespaceId);
  }

  @Override
  public StreamConfig getConfig(StreamId streamId) {
    throw new UnsupportedOperationException("Stream config not supported for non-file based stream.");
  }

  @Override
  public StreamProperties getProperties(StreamId streamId) {
    throw new UnsupportedOperationException("Stream properties not supported for non-file based stream.");
  }

  @Override
  public void updateConfig(StreamId streamId, StreamProperties properties) throws IOException {
    throw new UnsupportedOperationException("Stream config not supported for non-file based stream.");
  }

  @Override
  public boolean exists(StreamId streamId) throws Exception {
    return exists(QueueName.fromStream(streamId));
  }

  @Override
  @Nullable
  public StreamConfig create(StreamId streamId) throws Exception {
    create(QueueName.fromStream(streamId));
    publishAudit(streamId, AuditType.CREATE);
    return null;
  }

  @Override
  @Nullable
  public StreamConfig create(StreamId streamId, @Nullable Properties props) throws Exception {
    create(QueueName.fromStream(streamId), props);
    String description = (props != null) ? props.getProperty(Constants.Stream.DESCRIPTION) : null;
    streamMetaStore.addStream(streamId, description);
    publishAudit(streamId, AuditType.CREATE);
    return null;
  }

  @Override
  public void truncate(StreamId streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    truncate(QueueName.fromStream(streamId));
    publishAudit(streamId, AuditType.TRUNCATE);
  }

  @Override
  public void drop(StreamId streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    // Remove metadata for the stream
    metadataStore.removeMetadata(streamId);
    drop(QueueName.fromStream(streamId));
    streamMetaStore.removeStream(streamId);
    publishAudit(streamId, AuditType.DELETE);
  }

  @Override
  public boolean createOrUpdateView(StreamViewId viewId, ViewSpecification spec) throws Exception {
    Preconditions.checkArgument(exists(viewId.getParent()), "Stream '%s' does not exist.", viewId.getStream());
    return viewAdmin.createOrUpdate(viewId, spec);
  }

  @Override
  public void deleteView(StreamViewId viewId) throws Exception {
    Preconditions.checkArgument(exists(viewId.getParent()), "Stream '%s' does not exist.", viewId.getStream());
    viewAdmin.delete(viewId);
  }

  @Override
  public List<StreamViewId> listViews(StreamId streamId) throws Exception {
    Preconditions.checkArgument(exists(streamId), "Stream '%s' does not exist.", streamId);
    return viewAdmin.list(streamId);
  }

  @Override
  public ViewSpecification getView(StreamViewId viewId) throws Exception {
    Preconditions.checkArgument(exists(viewId.getParent()), "Stream '%s' does not exist.", viewId.getStream());
    return viewAdmin.get(viewId);
  }

  @Override
  public boolean viewExists(StreamViewId viewId) throws Exception {
    if (!exists(viewId.getParent())) {
      throw new StreamNotFoundException(viewId.getParent());
    }
    return viewAdmin.exists(viewId);
  }

  @Override
  public void register(Iterable<? extends EntityId> owners, StreamId streamId) {
    usageRegistry.registerAll(owners, streamId);
  }

  @Override
  public void addAccess(ProgramRunId run, StreamId streamId, AccessType accessType) {
    lineageWriter.addAccess(run, streamId, accessType);
    AuditPublishers.publishAccess(auditPublisher, streamId, accessType, run);
  }

  private void publishAudit(StreamId stream, AuditType auditType) {
    AuditPublishers.publishAudit(auditPublisher, stream, auditType, AuditPayload.EMPTY_PAYLOAD);
  }
}
