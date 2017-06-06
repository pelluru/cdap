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

package co.cask.cdap.etl.spark.streaming.function;

import co.cask.cdap.api.Transactionals;
import co.cask.cdap.api.TxRunnable;
import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.spark.JavaSparkExecutionContext;
import co.cask.cdap.etl.api.batch.SparkCompute;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import co.cask.cdap.etl.planner.StageInfo;
import co.cask.cdap.etl.spark.batch.BasicSparkExecutionPluginContext;
import co.cask.cdap.etl.spark.function.PluginFunctionContext;
import co.cask.cdap.etl.spark.streaming.DynamicDriverContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.tephra.TransactionFailureException;

/**
 * This class is required to make sure that macro substitution occurs each time a pipeline is run instead of just
 * the first time the pipeline is run. Without this, the SparkCompute plugin gets serialized into the checkpoint and
 * re-loaded every subsequent time with the properties from the first run.
 *
 * @param <T> type of object in the input collection
 * @param <U> type of object in the output collection
 */
public class DynamicSparkCompute<T, U> extends SparkCompute<T, U> {
  private final DynamicDriverContext dynamicDriverContext;
  private transient SparkCompute<T, U> delegate;

  public DynamicSparkCompute(DynamicDriverContext dynamicDriverContext, SparkCompute<T, U> compute) {
    this.dynamicDriverContext = dynamicDriverContext;
    this.delegate = compute;
  }

  @Override
  public void initialize(SparkExecutionPluginContext context) throws Exception {
    delegate.initialize(context);
  }

  @Override
  public JavaRDD<U> transform(SparkExecutionPluginContext context, JavaRDD<T> input) throws Exception {
    lazyInit(JavaSparkContext.fromSparkContext(input.context()));
    return delegate.transform(context, input);
  }

  // when checkpointing is enabled, and Spark is loading DStream operations from an existing checkpoint,
  // delegate will be null and the initialize() method won't have been called. So we need to instantiate
  // the delegate and initialize it.
  private void lazyInit(final JavaSparkContext jsc) throws Exception {
    if (delegate == null) {
      PluginFunctionContext pluginFunctionContext = dynamicDriverContext.getPluginFunctionContext();
      delegate = pluginFunctionContext.createPlugin();
      final StageInfo stageInfo = pluginFunctionContext.getStageInfo();
      final JavaSparkExecutionContext sec = dynamicDriverContext.getSparkExecutionContext();
      Transactionals.execute(sec, new TxRunnable() {
        @Override
        public void run(DatasetContext datasetContext) throws Exception {
          SparkExecutionPluginContext sparkPluginContext =
            new BasicSparkExecutionPluginContext(sec, jsc, datasetContext, stageInfo);
          delegate.initialize(sparkPluginContext);
        }
      }, Exception.class);
    }
  }
}
