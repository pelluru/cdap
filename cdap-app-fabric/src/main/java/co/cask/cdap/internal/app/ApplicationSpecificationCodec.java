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

package co.cask.cdap.internal.app;

import co.cask.cdap.api.app.ApplicationSpecification;
import co.cask.cdap.api.artifact.ArtifactId;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.flow.FlowSpecification;
import co.cask.cdap.api.mapreduce.MapReduceSpecification;
import co.cask.cdap.api.plugin.Plugin;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.api.service.ServiceSpecification;
import co.cask.cdap.api.spark.SparkSpecification;
import co.cask.cdap.api.worker.WorkerSpecification;
import co.cask.cdap.api.workflow.WorkflowSpecification;
import co.cask.cdap.internal.dataset.DatasetCreationSpec;
import co.cask.cdap.internal.schedule.ScheduleCreationSpec;
import co.cask.cdap.proto.codec.AbstractSpecificationCodec;
import co.cask.cdap.proto.id.ApplicationId;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * TODO: Move to cdap-proto
 */
final class ApplicationSpecificationCodec extends AbstractSpecificationCodec<ApplicationSpecification> {

  @Override
  public JsonElement serialize(ApplicationSpecification src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    jsonObj.add("name", new JsonPrimitive(src.getName()));
    jsonObj.add("appVersion", new JsonPrimitive(src.getAppVersion()));
    if (src.getConfiguration() != null) {
      jsonObj.add("configuration", new JsonPrimitive(src.getConfiguration()));
    }
    jsonObj.add("artifactId", context.serialize(src.getArtifactId()));
    jsonObj.add("description", new JsonPrimitive(src.getDescription()));
    jsonObj.add("streams", serializeMap(src.getStreams(), context, StreamSpecification.class));
    jsonObj.add("datasetModules", serializeMap(src.getDatasetModules(), context, String.class));
    jsonObj.add("datasetInstances", serializeMap(src.getDatasets(), context, DatasetCreationSpec.class));
    jsonObj.add("flows", serializeMap(src.getFlows(), context, FlowSpecification.class));
    jsonObj.add("mapReduces", serializeMap(src.getMapReduce(), context, MapReduceSpecification.class));
    jsonObj.add("sparks", serializeMap(src.getSpark(), context, SparkSpecification.class));
    jsonObj.add("workflows", serializeMap(src.getWorkflows(), context, WorkflowSpecification.class));
    jsonObj.add("services", serializeMap(src.getServices(), context, ServiceSpecification.class));
    jsonObj.add("schedules", serializeMap(src.getSchedules(), context, ScheduleSpecification.class));
    jsonObj.add("programSchedules", serializeMap(src.getProgramSchedules(), context, ScheduleCreationSpec.class));
    jsonObj.add("workers", serializeMap(src.getWorkers(), context, WorkerSpecification.class));
    jsonObj.add("plugins", serializeMap(src.getPlugins(), context, Plugin.class));

    return jsonObj;
  }

  @Override
  public ApplicationSpecification deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String name = jsonObj.get("name").getAsString();
    String appVersion = ApplicationId.DEFAULT_VERSION;
    if (jsonObj.has("appVersion")) {
      appVersion = jsonObj.get("appVersion").getAsString();
    }

    String description = jsonObj.get("description").getAsString();
    String configuration = null;
    if (jsonObj.has("configuration")) {
      configuration = jsonObj.get("configuration").getAsString();
    }

    ArtifactId artifactId = context.deserialize(jsonObj.get("artifactId"), ArtifactId.class);

    Map<String, StreamSpecification> streams = deserializeMap(jsonObj.get("streams"),
                                                              context, StreamSpecification.class);
    Map<String, String> datasetModules = deserializeMap(jsonObj.get("datasetModules"), context, String.class);
    Map<String, DatasetCreationSpec> datasetInstances = deserializeMap(jsonObj.get("datasetInstances"),
                                                                       context,
                                                                       DatasetCreationSpec.class);
    Map<String, FlowSpecification> flows = deserializeMap(jsonObj.get("flows"),
                                                          context, FlowSpecification.class);
    Map<String, MapReduceSpecification> mapReduces = deserializeMap(jsonObj.get("mapReduces"),
                                                                    context, MapReduceSpecification.class);
    Map<String, SparkSpecification> sparks = deserializeMap(jsonObj.get("sparks"),
                                                            context, SparkSpecification.class);
    Map<String, WorkflowSpecification> workflows = deserializeMap(jsonObj.get("workflows"),
                                                                  context, WorkflowSpecification.class);

    Map<String, ServiceSpecification> services = deserializeMap(jsonObj.get("services"),
                                                                context, ServiceSpecification.class);

    Map<String, ScheduleSpecification> schedules = deserializeMap(jsonObj.get("schedules"),
                                                                  context, ScheduleSpecification.class);

    Map<String, ScheduleCreationSpec> programSchedules = deserializeMap(jsonObj.get("programSchedules"),
                                                                        context, ScheduleCreationSpec.class);

    Map<String, WorkerSpecification> workers = deserializeMap(jsonObj.get("workers"), context,
                                                              WorkerSpecification.class);
    Map<String, Plugin> plugins = deserializeMap(jsonObj.get("plugins"), context, Plugin.class);

    return new DefaultApplicationSpecification(name, appVersion, description, configuration, artifactId, streams,
                                               datasetModules, datasetInstances,
                                               flows, mapReduces, sparks,
                                               workflows, services, schedules, programSchedules, workers, plugins);
  }
}
