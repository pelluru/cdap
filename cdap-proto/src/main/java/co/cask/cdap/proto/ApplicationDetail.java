/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package co.cask.cdap.proto;

import co.cask.cdap.api.ProgramSpecification;
import co.cask.cdap.api.app.ApplicationSpecification;
import co.cask.cdap.api.artifact.ArtifactSummary;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.plugin.Plugin;
import co.cask.cdap.internal.dataset.DatasetCreationSpec;
import co.cask.cdap.proto.id.ApplicationId;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Represents an application returned for /apps/{app-id}.
 */
public class ApplicationDetail {
  private final String name;
  private final String appVersion;
  private final String artifactVersion;
  private final String description;
  private final String configuration;
  private final List<StreamDetail> streams;
  private final List<DatasetDetail> datasets;
  private final List<ProgramRecord> programs;
  private final List<PluginDetail> plugins;
  private final ArtifactSummary artifact;
  @SerializedName("principal")
  private final String ownerPrincipal;

  public ApplicationDetail(String name,
                           String description,
                           String configuration,
                           List<StreamDetail> streams,
                           List<DatasetDetail> datasets,
                           List<ProgramRecord> programs,
                           List<PluginDetail> plugins,
                           ArtifactSummary artifact) {
    this(name, ApplicationId.DEFAULT_VERSION, description, configuration, streams, datasets, programs,
         plugins, artifact, null);
  }

  public ApplicationDetail(String name,
                           String description,
                           String configuration,
                           List<StreamDetail> streams,
                           List<DatasetDetail> datasets,
                           List<ProgramRecord> programs,
                           List<PluginDetail> plugins,
                           ArtifactSummary artifact,
                           @Nullable String ownerPrincipal) {
    this(name, ApplicationId.DEFAULT_VERSION, description, configuration, streams, datasets, programs,
         plugins, artifact, ownerPrincipal);
  }

  public ApplicationDetail(String name,
                           String appVersion,
                           String description,
                           String configuration,
                           List<StreamDetail> streams,
                           List<DatasetDetail> datasets,
                           List<ProgramRecord> programs,
                           List<PluginDetail> plugins,
                           ArtifactSummary artifact,
                           @Nullable String ownerPrincipal) {
    this.name = name;
    this.appVersion = appVersion;
    this.artifactVersion = artifact.getVersion();
    this.description = description;
    this.configuration = configuration;
    this.streams = streams;
    this.datasets = datasets;
    this.programs = programs;
    this.plugins = plugins;
    this.artifact = artifact;
    this.ownerPrincipal = ownerPrincipal;
  }

  public String getName() {
    return name;
  }
  public String getAppVersion() {
    return appVersion;
  }

  /**
   * @deprecated use {@link #getArtifact()} instead
   *
   * @return the artifactVersion of the artifact used to create the application
   */
  @Deprecated
  public String getArtifactVersion() {
    return artifactVersion;
  }

  public String getDescription() {
    return description;
  }

  public String getConfiguration() {
    return configuration;
  }

  public List<StreamDetail> getStreams() {
    return streams;
  }

  public List<DatasetDetail> getDatasets() {
    return datasets;
  }

  public List<ProgramRecord> getPrograms() {
    return programs;
  }

  public ArtifactSummary getArtifact() {
    return artifact;
  }

  @Nullable
  public String getOwnerPrincipal() {
    return ownerPrincipal;
  }

  public static ApplicationDetail fromSpec(ApplicationSpecification spec,
                                           @Nullable String ownerPrincipal) {
    List<ProgramRecord> programs = new ArrayList<>();
    for (ProgramSpecification programSpec : spec.getFlows().values()) {
      programs.add(new ProgramRecord(ProgramType.FLOW, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getMapReduce().values()) {
      programs.add(new ProgramRecord(ProgramType.MAPREDUCE, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getServices().values()) {
      programs.add(new ProgramRecord(ProgramType.SERVICE, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getSpark().values()) {
      programs.add(new ProgramRecord(ProgramType.SPARK, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getWorkers().values()) {
      programs.add(new ProgramRecord(ProgramType.WORKER, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }
    for (ProgramSpecification programSpec : spec.getWorkflows().values()) {
      programs.add(new ProgramRecord(ProgramType.WORKFLOW, spec.getName(),
                                     programSpec.getName(), programSpec.getDescription()));
    }

    List<StreamDetail> streams = new ArrayList<>();
    for (StreamSpecification streamSpec : spec.getStreams().values()) {
      streams.add(new StreamDetail(streamSpec.getName()));
    }

    List<DatasetDetail> datasets = new ArrayList<>();
    for (DatasetCreationSpec datasetSpec : spec.getDatasets().values()) {
      datasets.add(new DatasetDetail(datasetSpec.getInstanceName(), datasetSpec.getTypeName()));
    }

    List<PluginDetail> plugins = new ArrayList<>();
    for (Map.Entry<String, Plugin> pluginEnty : spec.getPlugins().entrySet()) {
      plugins.add(new PluginDetail(pluginEnty.getKey(),
                                   pluginEnty.getValue().getPluginClass().getName(),
                                   pluginEnty.getValue().getPluginClass().getType()));
    }
    // this is only required if there are old apps lying around that failed to get upgrading during
    // the upgrade to v3.2 for some reason. In those cases artifact id will be null until they re-deploy the app.
    // in the meantime, we don't want this api call to null pointer exception.
    ArtifactSummary summary = spec.getArtifactId() == null ?
      new ArtifactSummary(spec.getName(), null) : ArtifactSummary.from(spec.getArtifactId());
    return new ApplicationDetail(spec.getName(), spec.getAppVersion(), spec.getDescription(), spec.getConfiguration(),
                                 streams, datasets, programs, plugins, summary, ownerPrincipal);
  }
}
