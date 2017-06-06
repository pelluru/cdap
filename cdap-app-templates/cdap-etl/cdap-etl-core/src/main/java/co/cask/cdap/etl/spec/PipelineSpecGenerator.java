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

package co.cask.cdap.etl.spec;

import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.plugin.PluginConfigurer;
import co.cask.cdap.etl.api.Engine;
import co.cask.cdap.etl.api.ErrorTransform;
import co.cask.cdap.etl.api.MultiInputPipelineConfigurable;
import co.cask.cdap.etl.api.PipelineConfigurable;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.Transform;
import co.cask.cdap.etl.api.action.Action;
import co.cask.cdap.etl.api.batch.BatchAggregator;
import co.cask.cdap.etl.api.batch.BatchJoiner;
import co.cask.cdap.etl.api.batch.BatchSource;
import co.cask.cdap.etl.common.ArtifactSelectorProvider;
import co.cask.cdap.etl.common.Constants;
import co.cask.cdap.etl.common.DefaultPipelineConfigurer;
import co.cask.cdap.etl.common.DefaultStageConfigurer;
import co.cask.cdap.etl.planner.Dag;
import co.cask.cdap.etl.proto.Connection;
import co.cask.cdap.etl.proto.v2.ETLConfig;
import co.cask.cdap.etl.proto.v2.ETLPlugin;
import co.cask.cdap.etl.proto.v2.ETLStage;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This is run at application configure time to take an application config {@link ETLConfig} and call
 * {@link PipelineConfigurable#configurePipeline(PipelineConfigurer)} on all plugins in the pipeline.
 * This generates a {@link PipelineSpec} which the programs understand.
 *
 * @param <C> the type of user provided config
 * @param <P> the pipeline specification generated from the config
 */
public abstract class PipelineSpecGenerator<C extends ETLConfig, P extends PipelineSpec> {
  private static final Set<String> VALID_ERROR_INPUTS = ImmutableSet.of(
    BatchSource.PLUGIN_TYPE, Transform.PLUGIN_TYPE, BatchAggregator.PLUGIN_TYPE, ErrorTransform.PLUGIN_TYPE);
  protected final PluginConfigurer configurer;
  protected final Engine engine;
  private final Class<? extends Dataset> errorDatasetClass;
  private final DatasetProperties errorDatasetProperties;
  private final Set<String> sourcePluginTypes;
  private final Set<String> sinkPluginTypes;

  protected PipelineSpecGenerator(PluginConfigurer configurer,
                                  Set<String> sourcePluginTypes,
                                  Set<String> sinkPluginTypes,
                                  Class<? extends Dataset> errorDatasetClass,
                                  DatasetProperties errorDatasetProperties,
                                  Engine engine) {
    this.configurer = configurer;
    this.sourcePluginTypes = sourcePluginTypes;
    this.sinkPluginTypes = sinkPluginTypes;
    this.errorDatasetClass = errorDatasetClass;
    this.errorDatasetProperties = errorDatasetProperties;
    this.engine = engine;
  }

  /**
   * Validate the user provided ETL config and generate a pipeline specification from it.
   * It will also register all plugins used by the pipeline and create any error datasets used by the pipeline.
   *
   * A valid pipeline has the following properties:
   *
   * All stages in the pipeline have a unique name.
   * Source stages have at least one output and no inputs.
   * Sink stages have at least one input and no outputs.
   * There are no cycles in the pipeline.
   * All inputs into a stage have the same schema.
   *
   * @param config user provided ETL config
   */
  public abstract P generateSpec(C config);

  /**
   * Performs most of the validation and configuration needed by a pipeline.
   * Handles stages, connections, resources, and stage logging settings.
   *
   * @param config user provided ETL config
   * @param specBuilder builder for creating a pipeline spec.
   */
  protected void configureStages(ETLConfig config, PipelineSpec.Builder specBuilder) {
    // validate the config and determine the order we should configure the stages in.
    List<StageConnections> traversalOrder = validateConfig(config);

    Map<String, DefaultPipelineConfigurer> pluginConfigurers = new HashMap<>(traversalOrder.size());
    Map<String, String> pluginTypes = new HashMap<>(traversalOrder.size());
    for (StageConnections stageConnections : traversalOrder) {
      String stageName = stageConnections.getStage().getName();
      pluginTypes.put(stageName, stageConnections.getStage().getPlugin().getType());
      pluginConfigurers.put(stageName, new DefaultPipelineConfigurer(configurer, stageName, engine));
    }

    // anything prefixed by 'system.[engine].' is a pipeline property.
    Map<String, String> pipelineProperties = new HashMap<>();
    String prefix = String.format("system.%s.", engine.name().toLowerCase());
    int prefixLength = prefix.length();
    for (Map.Entry<String, String> property : config.getProperties().entrySet()) {
      if (property.getKey().startsWith(prefix)) {
        String strippedKey = property.getKey().substring(prefixLength);
        pipelineProperties.put(strippedKey, property.getValue());
      }
    }

    // row = property name, column = property value, val = stage that set the property
    // this is used so that we can error with a nice message about which stages are setting conflicting properties
    Table<String, String, String> propertiesFromStages = HashBasedTable.create();
    // configure the stages in order and build up the stage specs
    for (StageConnections stageConnections : traversalOrder) {
      ETLStage stage = stageConnections.getStage();
      String stageName = stage.getName();
      DefaultPipelineConfigurer pluginConfigurer = pluginConfigurers.get(stageName);

      ConfiguredStage configuredStage = configureStage(stageConnections, pluginConfigurer);
      Schema outputSchema = configuredStage.stageSpec.getOutputSchema();
      Schema outputErrorSchema = configuredStage.stageSpec.getErrorSchema();

      // for each output, set their input schema to our output schema
      for (String outputStageName : stageConnections.getOutputs()) {

        String outputStageType = pluginTypes.get(outputStageName);
        // no need to set any input schemas for an Action plug
        if (Action.PLUGIN_TYPE.equals(outputStageType)) {
          continue;
        }

        DefaultStageConfigurer outputStageConfigurer = pluginConfigurers.get(outputStageName).getStageConfigurer();

        // Do not allow null input schema for Joiner
        if (BatchJoiner.PLUGIN_TYPE.equals(outputStageType) && outputSchema == null) {
          throw new IllegalArgumentException(String.format("Joiner cannot have any null input schemas, but stage %s " +
                                                             "outputs a null schema.", stageName));
        }

        // if the output stage is an error transform, it takes the error schema of this stage as its input.
        // all other plugin types that the output schema of this stage as its input.
        Schema nextStageInputSchema = ErrorTransform.PLUGIN_TYPE.equals(outputStageType) ?
          outputErrorSchema : outputSchema;

        // Do not allow more than one input schema for stages other than Joiner
        if (!BatchJoiner.PLUGIN_TYPE.equals(outputStageType) &&
          !hasSameSchema(outputStageConfigurer.getInputSchemas(), nextStageInputSchema)) {
          throw new IllegalArgumentException("Two different input schema were set for the stage " + outputStageName);
        }

        outputStageConfigurer.addInputSchema(stageName, nextStageInputSchema);
      }
      specBuilder.addStage(configuredStage.stageSpec);
      for (Map.Entry<String, String> propertyEntry : configuredStage.pipelineProperties.entrySet()) {
        propertiesFromStages.put(propertyEntry.getKey(), propertyEntry.getValue(), stageName);
      }
    }

    // check that multiple stages did not set conflicting properties
    for (String propertyName : propertiesFromStages.rowKeySet()) {
      // go through all values set for the property name. If there is more than one, we have a conflict.
      Map<String, String> propertyValues = propertiesFromStages.row(propertyName);
      if (propertyValues.size() > 1) {
        StringBuilder errMsg = new StringBuilder("Pipeline property '")
          .append(propertyName)
          .append("' is being set to different values by stages.");
        for (Map.Entry<String, String> valueEntry : propertyValues.entrySet()) {
          String propertyValue = valueEntry.getKey();
          String fromStage = valueEntry.getValue();
          errMsg.append(" stage '").append(fromStage).append("' = '").append(propertyValue).append("',");
        }
        errMsg.deleteCharAt(errMsg.length() - 1);
        throw new IllegalArgumentException(errMsg.toString());
      }
      pipelineProperties.put(propertyName, propertyValues.keySet().iterator().next());
    }

    specBuilder.addConnections(config.getConnections())
      .setResources(config.getResources())
      .setDriverResources(config.getDriverResources())
      .setClientResources(config.getClientResources())
      .setStageLoggingEnabled(config.isStageLoggingEnabled())
      .setNumOfRecordsPreview(config.getNumOfRecordsPreview())
      .setProperties(pipelineProperties)
      .build();
  }

  private boolean hasSameSchema(Map<String, Schema> inputSchemas, Schema inputSchema) {
    if (!inputSchemas.isEmpty()) {
      if (!Objects.equals(inputSchemas.values().iterator().next(), inputSchema)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Configures a stage and returns the spec for it.
   *
   * @param stageConnections the user provided configuration for the stage along with its connections
   * @param pluginConfigurer configurer used to configure the stage
   * @return the spec for the stage
   */
  private ConfiguredStage configureStage(StageConnections stageConnections,
                                         DefaultPipelineConfigurer pluginConfigurer) {
    ETLStage stage = stageConnections.getStage();
    String stageName = stage.getName();
    ETLPlugin stagePlugin = stage.getPlugin();

    if (!Strings.isNullOrEmpty(stage.getErrorDatasetName())) {
      configurer.createDataset(stage.getErrorDatasetName(), errorDatasetClass, errorDatasetProperties);
    }

    PluginSpec pluginSpec = configurePlugin(stageName, stagePlugin, pluginConfigurer);
    Schema outputSchema = pluginConfigurer.getStageConfigurer().getOutputSchema();
    Map<String, Schema> inputSchemas = pluginConfigurer.getStageConfigurer().getInputSchemas();
    StageSpec stageSpec = StageSpec.builder(stageName, pluginSpec)
      .setErrorDatasetName(stage.getErrorDatasetName())
      .addInputSchemas(inputSchemas)
      .setOutputSchema(outputSchema)
      .setErrorSchema(pluginConfigurer.getStageConfigurer().getErrorSchema())
      .addInputs(stageConnections.getInputs())
      .addOutputs(stageConnections.getOutputs())
      .build();
    return new ConfiguredStage(stageSpec, pluginConfigurer.getPipelineProperties());
  }


  /**
   * Configures a plugin and returns the spec for it.
   *
   * @param pluginId the unique plugin id
   * @param etlPlugin user provided configuration for the plugin
   * @param pipelineConfigurer default pipeline configurere to configure the plugin
   * @return the spec for the plugin
   */
  protected PluginSpec configurePlugin(String pluginId, ETLPlugin etlPlugin,
                                       DefaultPipelineConfigurer pipelineConfigurer) {
    TrackedPluginSelector pluginSelector = new TrackedPluginSelector(
      new ArtifactSelectorProvider(etlPlugin.getType(), etlPlugin.getName())
        .getPluginSelector(etlPlugin.getArtifactConfig()));
    String type = etlPlugin.getType();
    Object plugin = configurer.usePlugin(etlPlugin.getType(),
                                         etlPlugin.getName(),
                                         pluginId,
                                         etlPlugin.getPluginProperties(),
                                         pluginSelector);

    if (plugin == null) {
      throw new IllegalArgumentException(
        String.format("No plugin of type %s and name %s could be found for stage %s.",
                      etlPlugin.getType(), etlPlugin.getName(), pluginId));
    }
    try {
      if (type.equals(BatchJoiner.PLUGIN_TYPE)) {
        MultiInputPipelineConfigurable multiPlugin = (MultiInputPipelineConfigurable) plugin;
        multiPlugin.configurePipeline(pipelineConfigurer);
      } else if (!type.equals(Constants.SPARK_PROGRAM_PLUGIN_TYPE)) {
        PipelineConfigurable singlePlugin = (PipelineConfigurable) plugin;
        singlePlugin.configurePipeline(pipelineConfigurer);
      }
    } catch (Exception e) {
      throw new RuntimeException(
        String.format("Exception while configuring plugin of type %s and name %s for stage %s: %s",
                      etlPlugin.getType(), etlPlugin.getName(), pluginId, e.getMessage()),
        e);
    }
    return new PluginSpec(etlPlugin.getType(),
                          etlPlugin.getName(),
                          etlPlugin.getProperties(),
                          pluginSelector.getSelectedArtifact());
  }

  /**
   * Validate that this is a valid pipeline. A valid pipeline has the following properties:
   *
   * All stages in the pipeline have a unique name.
   * Source stages have at least one output and no inputs.
   * Sink stages have at least one input and no outputs.
   * There are no cycles in the pipeline.
   * All inputs into a stage have the same schema.
   * ErrorTransforms only have BatchSource, Transform, or BatchAggregator as input stages
   *
   * Returns the stages in the order they should be configured to ensure that all input stages are configured
   * before their output.
   *
   * @param config the user provided configuration
   * @return the order to configure the stages in
   * @throws IllegalArgumentException if the pipeline is invalid
   */
  private List<StageConnections> validateConfig(ETLConfig config) {
    config.validate();
    if (config.getStages().isEmpty()) {
      throw new IllegalArgumentException("A pipeline must contain at least one stage.");
    }

    Set<String> actionStages = new HashSet<>();
    Map<String, String> stageTypes = new HashMap<>();
    // check stage name uniqueness
    Set<String> stageNames = new HashSet<>();
    for (ETLStage stage : config.getStages()) {
      if (!stageNames.add(stage.getName())) {
        throw new IllegalArgumentException(
          String.format("Invalid pipeline. Multiple stages are named %s. Please ensure all stage names are unique",
                        stage.getName()));
      }
      // if stage is Action stage, add it to the Action stage set
      if (isAction(stage.getPlugin().getType())) {
        actionStages.add(stage.getName());
      }
      stageTypes.put(stage.getName(), stage.getPlugin().getType());
    }

    // check that the from and to are names of actual stages
    for (Connection connection : config.getConnections()) {
      if (!stageNames.contains(connection.getFrom())) {
        throw new IllegalArgumentException(
          String.format("Invalid connection %s. %s is not a stage.", connection, connection.getFrom()));
      }
      if (!stageNames.contains(connection.getTo())) {
        throw new IllegalArgumentException(
          String.format("Invalid connection %s. %s is not a stage.", connection, connection.getTo()));
      }
    }

    List<StageConnections> traversalOrder = new ArrayList<>(stageNames.size());

    // can only have empty connections if the pipeline consists of a single action.
    if (config.getConnections().isEmpty()) {
      if (actionStages.size() == 1 && stageNames.size() == 1) {
        traversalOrder.add(new StageConnections(config.getStages().iterator().next(),
                                                Collections.<String>emptyList(),
                                                Collections.<String>emptyList()));
        return traversalOrder;
      } else {
        throw new IllegalArgumentException(
          "Invalid pipeline. There are no connections between stages. " +
            "This is only allowed if the pipeline consists of a single action plugin.");
      }
    }

    Dag dag = new Dag(config.getConnections());

    Map<String, StageConnections> stages = new HashMap<>();
    for (ETLStage stage : config.getStages()) {
      String stageName = stage.getName();
      Set<String> stageInputs = dag.getNodeInputs(stageName);
      Set<String> stageOutputs = dag.getNodeOutputs(stageName);
      String stageType = stage.getPlugin().getType();

      // check source plugins are sources in the dag
      if (isSource(stageType)) {
        if (!stageInputs.isEmpty() && !actionStages.containsAll(stageInputs)) {
          throw new IllegalArgumentException(
            String.format("Source %s has incoming connections from %s. Sources cannot have any incoming connections.",
                          stageName, Joiner.on(',').join(stageInputs)));
        }
      } else if (isSink(stageType)) {
        if (!stageOutputs.isEmpty() && !actionStages.containsAll(stageOutputs)) {
          throw new IllegalArgumentException(
            String.format("Sink %s has outgoing connections to %s. Sinks cannot have any outgoing connections.",
                          stageName, Joiner.on(',').join(stageOutputs)));
        }
      } else {
        // check that other non-action plugins are not sources or sinks in the dag
        if (!isAction(stageType)) {
          if (stageInputs.isEmpty()) {
            throw new IllegalArgumentException(
              String.format("Stage %s is unreachable, it has no incoming connections.", stageName));
          }
          if (stageOutputs.isEmpty()) {
            throw new IllegalArgumentException(
              String.format("Stage %s is a dead end, it has no outgoing connections.", stageName));
          }
        }

        // check that error transforms only have stages that can emit errors as input
        boolean isErrorTransform = ErrorTransform.PLUGIN_TYPE.equals(stageType);
        if (isErrorTransform) {
          for (String inputStage : stageInputs) {
            String inputType = stageTypes.get(inputStage);
            if (!VALID_ERROR_INPUTS.contains(inputType)) {
              throw new IllegalArgumentException(String.format(
                "ErrorTransform %s cannot have stage %s of type %s as input. Only %s stages can emit errors.",
                stageName, inputStage, inputType, Joiner.on(',').join(VALID_ERROR_INPUTS)));
            }
          }
        }
      }

      stages.put(stageName, new StageConnections(stage, stageInputs, stageOutputs));
    }

    for (String stageName : dag.getTopologicalOrder()) {
      traversalOrder.add(stages.get(stageName));
    }

    return traversalOrder;
  }

  // will soon have another action type
  private boolean isAction(String pluginType) {
    return Action.PLUGIN_TYPE.equals(pluginType) || Constants.SPARK_PROGRAM_PLUGIN_TYPE.equals(pluginType);
  }

  private boolean isSource(String pluginType) {
    return sourcePluginTypes.contains(pluginType);
  }

  private boolean isSink(String pluginType) {
    return sinkPluginTypes.contains(pluginType);
  }

  /**
   * Just a container for StageSpec and pipeline properties set by the stage
   */
  private static class ConfiguredStage {
    private final StageSpec stageSpec;
    private final Map<String, String> pipelineProperties;

    private ConfiguredStage(StageSpec stageSpec, Map<String, String> pipelineProperties) {
      this.stageSpec = stageSpec;
      this.pipelineProperties = pipelineProperties;
    }
  }
}
