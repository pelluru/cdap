/*
 * Copyright © 2014-2017 Cask Data, Inc.
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
package co.cask.cdap.metrics.guice;

import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import co.cask.cdap.api.metrics.MetricValues;
import co.cask.cdap.api.metrics.MetricsCollectionService;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.io.DatumWriter;
import co.cask.cdap.internal.io.DatumWriterFactory;
import co.cask.cdap.internal.io.SchemaGenerator;
import co.cask.cdap.metrics.collect.MessagingMetricsCollectionService;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;

/**
 * Guice module for binding classes for metrics client in distributed runtime mode.
 * Requires binding from {@link co.cask.cdap.common.guice.KafkaClientModule} and
 * {@link co.cask.cdap.common.guice.IOModule}.
 */
final class DistributedMetricsClientModule extends PrivateModule {

  private static final TypeToken<MetricValues> METRIC_RECORD_TYPE = TypeToken.of(MetricValues.class);

  @Override
  protected void configure() {
    bind(MetricsCollectionService.class).to(MessagingMetricsCollectionService.class).in(Scopes.SINGLETON);
    expose(MetricsCollectionService.class);
  }

  @Provides
  @Named(Constants.Metrics.TOPIC_PREFIX)
  public String providesTopicPrefix(CConfiguration cConf) {
    return cConf.get(Constants.Metrics.TOPIC_PREFIX);
  }

  @Provides
  @Named(Constants.Metrics.MESSAGING_TOPIC_NUM)
  public int providesMessagingTopicPartition(CConfiguration cConf) {
    return cConf.getInt(Constants.Metrics.MESSAGING_TOPIC_NUM);
  }

  @Provides
  public DatumWriter<MetricValues> providesDatumWriter(SchemaGenerator schemaGenerator,
                                                        DatumWriterFactory datumWriterFactory) {
    try {
      return datumWriterFactory.create(METRIC_RECORD_TYPE, schemaGenerator.generate(METRIC_RECORD_TYPE.getType()));
    } catch (UnsupportedTypeException e) {
      throw Throwables.propagate(e);
    }
  }
}
