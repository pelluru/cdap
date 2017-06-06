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

package co.cask.cdap.app.runtime.spark

import java.net.URI

import co.cask.cdap.api.data.DatasetInstantiationException
import co.cask.cdap.api.data.batch.{BatchReadable, InputFormatProvider, Split}
import co.cask.cdap.api.dataset.Dataset
import co.cask.cdap.common.conf.ConfigurationUtil
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.InputFormat
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, SparkContext, TaskContext}

import scala.annotation.meta.param
import scala.reflect.ClassTag

/**
  * A [[org.apache.spark.rdd.RDD]] for reading data from [[co.cask.cdap.api.dataset.Dataset]].
  */
class DatasetRDD[K: ClassTag, V: ClassTag](@(transient @param) sc: SparkContext,
                                           @(transient @param) datasetCompute: DatasetCompute,
                                           @(transient @param) hConf: Configuration,
                                           namespace: String,
                                           datasetName: String,
                                           arguments: Map[String, String],
                                           @(transient @param) splits: Option[Iterable[_ <: Split]],
                                           txServiceBaseURI: Broadcast[URI]) extends RDD[(K, V)](sc, Nil) {

  var delegateRDD: Option[RDD[(K, V)]] = None

  @DeveloperApi
  override def compute(split: Partition, context: TaskContext): Iterator[(K, V)] =
    delegateRDD.get.compute(split, context)

  override protected def getPartitions: Array[Partition] = {
    if (delegateRDD.isEmpty) {
      delegateRDD = Some(datasetCompute(namespace, datasetName, arguments, (dataset: Dataset) => {
        // Depends on whether it is a BatchReadable or an InputFormatProvider, constructs a corresponding
        // RDD that this RDD delegates to
        dataset match {
          case batchReadable: BatchReadable[K, V] => {
            new BatchReadableRDD[K, V](sc, batchReadable, namespace, datasetName, arguments, splits,
              txServiceBaseURI)
          }

          case inputFormatProvider: InputFormatProvider => {
            // Use the Spark newAPIHadoopRDD
            val inputFormatClassName = Option(inputFormatProvider.getInputFormatClassName).getOrElse(
              throw new DatasetInstantiationException("No input format class from dataset '" + datasetName + "'"))
            val conf = ConfigurationUtil.setAll(inputFormatProvider.getInputFormatConfiguration,
                                                new Configuration(hConf))
            val inputFormatClass = SparkClassLoader.findFromContext()
                                                   .loadClass(inputFormatClassName)
                                                   .asInstanceOf[Class[InputFormat[K, V]]]

            val keyClass: Class[K] = implicitly[ClassManifest[K]].runtimeClass.asInstanceOf[Class[K]]
            val valueClass: Class[V] = implicitly[ClassManifest[V]].runtimeClass.asInstanceOf[Class[V]]
            sc.newAPIHadoopRDD(conf, inputFormatClass, keyClass, valueClass)
          }

          case _ => throw new IllegalArgumentException("Unsupport dataset type " + dataset.getClass)
        }
      }))
    }

    delegateRDD.get.partitions
  }
}
