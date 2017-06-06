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

package co.cask.cdap.data2.dataset2.lib.table.inmemory;

import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetContext;
import co.cask.cdap.common.conf.CConfiguration;

import java.io.IOException;

/**
 *
 */
public class InMemoryTableAdmin implements DatasetAdmin {
  private final String name;

  public InMemoryTableAdmin(DatasetContext datasetContext, String name, CConfiguration cConf) {
    this.name = PrefixedNamespaces.namespace(cConf, datasetContext.getNamespaceId(), name);
  }

  @Override
  public boolean exists() {
    return InMemoryTableService.exists(name);
  }

  @Override
  public void create() {
    InMemoryTableService.create(name);
  }

  @Override
  public void truncate() {
    InMemoryTableService.truncate(name);
  }

  @Override
  public void drop() {
    InMemoryTableService.drop(name);
  }

  @Override
  public void upgrade() {
    // no-op
  }

  @Override
  public void close() throws IOException {
    // NOTHING to do
  }
}
