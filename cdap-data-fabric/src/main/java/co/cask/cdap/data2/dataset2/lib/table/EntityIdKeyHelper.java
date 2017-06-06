/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.data2.dataset2.lib.table;

import co.cask.cdap.proto.element.EntityTypeSimpleName;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.FlowId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NamespacedEntityId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.ServiceId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.id.StreamViewId;
import co.cask.cdap.proto.id.WorkflowId;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Helper methods to create keys for {@link co.cask.cdap.proto.id.EntityId}.
 * This should be used in place of {@link EntityId#toString()} while persisting {@link EntityId} to avoid
 * incompatibility issues across CDAP upgrades.
 */
// Note: these methods were refactored from MetadataDataset class. Once CDAP-3657 is fixed, these methods will need
// to be cleaned up CDAP-4291
public final class EntityIdKeyHelper {
  public static final Map<Class<? extends NamespacedEntityId>, String> TYPE_MAP =
    ImmutableMap.<Class<? extends NamespacedEntityId>, String>builder()
      .put(NamespaceId.class, EntityTypeSimpleName.NAMESPACE.getSerializedForm())
      .put(ArtifactId.class, EntityTypeSimpleName.ARTIFACT.getSerializedForm())
      .put(ApplicationId.class, EntityTypeSimpleName.APP.getSerializedForm())
      .put(ProgramId.class, EntityTypeSimpleName.PROGRAM.getSerializedForm())
      .put(WorkflowId.class, EntityTypeSimpleName.PROGRAM.getSerializedForm())
      .put(FlowId.class, EntityTypeSimpleName.PROGRAM.getSerializedForm())
      .put(ServiceId.class, EntityTypeSimpleName.PROGRAM.getSerializedForm())
      .put(DatasetId.class, EntityTypeSimpleName.DATASET.getSerializedForm())
      .put(StreamId.class, EntityTypeSimpleName.STREAM.getSerializedForm())
      .put(StreamViewId.class, EntityTypeSimpleName.VIEW.getSerializedForm())
      .build();

  public static void addTargetIdToKey(MDSKey.Builder builder, NamespacedEntityId namespacedEntityId) {
    String type = getTargetType(namespacedEntityId);
    if (type.equals(TYPE_MAP.get(NamespaceId.class))) {
      NamespaceId namespaceId = (NamespaceId) namespacedEntityId;
      builder.add(namespaceId.getNamespace());
    } else if (type.equals(TYPE_MAP.get(ProgramId.class))) {
      ProgramId program = (ProgramId) namespacedEntityId;
      String namespaceId = program.getNamespace();
      String appId = program.getApplication();
      String programType = program.getType().name();
      String programId = program.getProgram();
      builder.add(namespaceId);
      builder.add(appId);
      builder.add(programType);
      builder.add(programId);
    } else if (type.equals(TYPE_MAP.get(ApplicationId.class))) {
      ApplicationId application = (ApplicationId) namespacedEntityId;
      String namespaceId = application.getNamespace();
      String appId = application.getApplication();
      builder.add(namespaceId);
      builder.add(appId);
    } else if (type.equals(TYPE_MAP.get(DatasetId.class))) {
      DatasetId datasetInstance = (DatasetId) namespacedEntityId;
      String namespaceId = datasetInstance.getNamespace();
      String datasetId = datasetInstance.getDataset();
      builder.add(namespaceId);
      builder.add(datasetId);
    } else if (type.equals(TYPE_MAP.get(StreamId.class))) {
      StreamId stream = (StreamId) namespacedEntityId;
      String namespaceId = stream.getNamespace();
      String streamId = stream.getStream();
      builder.add(namespaceId);
      builder.add(streamId);
    } else if (type.equals(TYPE_MAP.get(StreamViewId.class))) {
      StreamViewId view = (StreamViewId) namespacedEntityId;
      String namespaceId = view.getNamespace();
      String streamId = view.getStream();
      String viewId = view.getView();
      builder.add(namespaceId);
      builder.add(streamId);
      builder.add(viewId);
    } else if (type.equals(TYPE_MAP.get(ArtifactId.class))) {
      ArtifactId artifactId = (ArtifactId) namespacedEntityId;
      String namespaceId = artifactId.getNamespace();
      String name = artifactId.getArtifact();
      String version = artifactId.getVersion();
      builder.add(namespaceId);
      builder.add(name);
      builder.add(version);
    } else {
      throw new IllegalArgumentException("Illegal Type " + type + " of metadata source.");
    }
  }

  public static NamespacedEntityId getTargetIdIdFromKey(MDSKey.Splitter keySplitter, String type) {
    if (type.equals(TYPE_MAP.get(NamespaceId.class))) {
      String namespaceId = keySplitter.getString();
      return new NamespaceId(namespaceId);
    } else if (type.equals(TYPE_MAP.get(ProgramId.class))) {
      String namespaceId = keySplitter.getString();
      String appId = keySplitter.getString();
      String programType = keySplitter.getString();
      String programId = keySplitter.getString();
      return new ProgramId(namespaceId, appId, programType, programId);
    } else if (type.equals(TYPE_MAP.get(ApplicationId.class))) {
      String namespaceId = keySplitter.getString();
      String appId = keySplitter.getString();
      return new ApplicationId(namespaceId, appId);
    } else if (type.equals(TYPE_MAP.get(ArtifactId.class))) {
      String namespaceId = keySplitter.getString();
      String name = keySplitter.getString();
      String version = keySplitter.getString();
      return new ArtifactId(namespaceId, name, version);
    } else if (type.equals(TYPE_MAP.get(DatasetId.class))) {
      String namespaceId = keySplitter.getString();
      String instanceId  = keySplitter.getString();
      return new DatasetId(namespaceId, instanceId);
    } else if (type.equals(TYPE_MAP.get(StreamId.class))) {
      String namespaceId = keySplitter.getString();
      String instanceId  = keySplitter.getString();
      return new StreamId(namespaceId, instanceId);
    } else if (type.equals(TYPE_MAP.get(StreamViewId.class))) {
      String namespaceId = keySplitter.getString();
      String streamId  = keySplitter.getString();
      String viewId = keySplitter.getString();
      return new StreamViewId(namespaceId, streamId, viewId);
    }
    throw new IllegalArgumentException("Illegal Type " + type + " of metadata source.");
  }

  public static String getTargetType(NamespacedEntityId namespacedEntityId) {
    return TYPE_MAP.get(namespacedEntityId.getClass());
  }

  private EntityIdKeyHelper() {
  }
}
