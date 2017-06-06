/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.data2.transaction.stream.inmemory;

import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data.stream.StreamUtils;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.InMemoryTable;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.InMemoryTableAdmin;
import co.cask.cdap.data2.dataset2.lib.table.inmemory.NoTxInMemoryTable;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.data2.transaction.stream.StreamConsumerStateStore;
import co.cask.cdap.data2.transaction.stream.StreamConsumerStateStoreFactory;
import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.proto.id.NamespaceId;
import com.google.inject.Inject;

import java.io.IOException;

/**
 * Factory for creating {@link StreamConsumerStateStore} in memory.
 */
public final class InMemoryStreamConsumerStateStoreFactory implements StreamConsumerStateStoreFactory {
  private final CConfiguration cConf;

  @Inject
  InMemoryStreamConsumerStateStoreFactory(CConfiguration cConf) {
    this.cConf = cConf;
  }

  @Override
  public synchronized StreamConsumerStateStore create(StreamConfig streamConfig) throws IOException {
    NamespaceId namespace = streamConfig.getStreamId().getParent();
    TableId tableId = StreamUtils.getStateStoreTableId(namespace);
    InMemoryTableAdmin admin =
      new InMemoryTableAdmin(DatasetContext.from(tableId.getNamespace()), tableId.getTableName(), cConf);
    if (!admin.exists()) {
      admin.create();
    }
    InMemoryTable table =
      new NoTxInMemoryTable(DatasetContext.from(tableId.getNamespace()), tableId.getTableName(), cConf);
    return new InMemoryStreamConsumerStateStore(streamConfig, table);
  }

  @Override
  public synchronized void dropAllInNamespace(NamespaceId namespace) throws IOException {
    TableId tableId = StreamUtils.getStateStoreTableId(namespace);
    InMemoryTableAdmin admin =
      new InMemoryTableAdmin(DatasetContext.from(tableId.getNamespace()), tableId.getTableName(), cConf);
    admin.drop();
  }
}
