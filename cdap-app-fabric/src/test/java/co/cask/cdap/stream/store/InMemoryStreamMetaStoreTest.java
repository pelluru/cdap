/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.stream.store;

import co.cask.cdap.data.stream.service.InMemoryStreamMetaStore;
import co.cask.cdap.data.stream.service.StreamMetaStore;

/**
 * Test the {@link InMemoryStreamMetaStore}.
 */
public class InMemoryStreamMetaStoreTest extends StreamMetaStoreTestBase {
  @Override
  protected StreamMetaStore getStreamMetaStore() {
    return new InMemoryStreamMetaStore();
  }

  @Override
  protected void createNamespace(String namespaceId) {
    // No-op
  }

  @Override
  protected void deleteNamespace(String namespaceId) {
    // No-op
  }
}
