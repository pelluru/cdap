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

package co.cask.cdap.etl.spark.function;

import co.cask.cdap.api.ServiceDiscoverer;
import co.cask.cdap.api.macro.MacroEvaluator;
import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.api.plugin.PluginContext;
import co.cask.cdap.api.preview.DataTracer;
import co.cask.cdap.api.security.store.SecureStore;
import co.cask.cdap.api.spark.JavaSparkExecutionContext;
import co.cask.cdap.api.workflow.WorkflowToken;
import co.cask.cdap.etl.api.StageMetrics;
import co.cask.cdap.etl.common.BasicArguments;
import co.cask.cdap.etl.common.DefaultMacroEvaluator;
import co.cask.cdap.etl.common.DefaultStageMetrics;
import co.cask.cdap.etl.common.plugin.PipelinePluginContext;
import co.cask.cdap.etl.planner.StageInfo;
import co.cask.cdap.etl.spark.batch.SparkBatchRuntimeContext;
import co.cask.cdap.etl.spark.plugin.SparkPipelinePluginContext;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializable collection of objects that can be used in Spark closures to instantiate plugins.
 */
public class PluginFunctionContext implements Serializable {
  private static final long serialVersionUID = 7591792350198264606L;

  private final String namespace;
  private final long logicalStartTime;
  private final Map<String, String> arguments;
  private final PluginContext pluginContext;
  private final ServiceDiscoverer serviceDiscoverer;
  private final Metrics metrics;
  private final SecureStore secureStore;
  private final DataTracer dataTracer;
  private final StageInfo stageInfo;
  private transient PipelinePluginContext pipelinePluginContext;

  public PluginFunctionContext(StageInfo stageInfo, JavaSparkExecutionContext sec) {
    this.namespace = sec.getNamespace();
    this.stageInfo = stageInfo;
    this.logicalStartTime = sec.getLogicalStartTime();
    Map<String, String> arguments = new HashMap<>();
    arguments.putAll(sec.getRuntimeArguments());
    WorkflowToken token = sec.getWorkflowToken();
    if (token != null) {
      for (String tokenKey : token.getAll(WorkflowToken.Scope.USER).keySet()) {
        arguments.put(tokenKey, token.get(tokenKey, WorkflowToken.Scope.USER).toString());
      }
    }
    this.arguments = arguments;
    this.pluginContext = sec.getPluginContext();
    this.serviceDiscoverer = sec.getServiceDiscoverer();
    this.metrics = sec.getMetrics();
    this.secureStore = sec.getSecureStore();
    this.dataTracer = sec.getDataTracer(stageInfo.getName());
    this.pipelinePluginContext = getPluginContext();
  }

  public <T> T createPlugin() throws Exception {
    MacroEvaluator macroEvaluator = new DefaultMacroEvaluator(arguments, logicalStartTime, secureStore, namespace);
    return getPluginContext().newPluginInstance(stageInfo.getName(), macroEvaluator);
  }

  public String getStageName() {
    return stageInfo.getName();
  }

  public StageInfo getStageInfo() {
    return stageInfo;
  }

  public StageMetrics createStageMetrics() {
    return new DefaultStageMetrics(metrics, stageInfo.getName());
  }

  public SparkBatchRuntimeContext createBatchRuntimeContext() {
    return new SparkBatchRuntimeContext(getPluginContext(), serviceDiscoverer, metrics, logicalStartTime, stageInfo,
                                        new BasicArguments(arguments));
  }

  public DataTracer getDataTracer() {
    return dataTracer;
  }

  private PipelinePluginContext getPluginContext() {
    if (pipelinePluginContext == null) {
      pipelinePluginContext = new SparkPipelinePluginContext(pluginContext, metrics,
                                                             stageInfo.isStageLoggingEnabled(),
                                                             stageInfo.isProcessTimingEnabled());
    }
    return pipelinePluginContext;
  }
}
