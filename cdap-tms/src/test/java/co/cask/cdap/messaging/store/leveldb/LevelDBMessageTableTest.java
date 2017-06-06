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

package co.cask.cdap.messaging.store.leveldb;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.messaging.store.MessageTable;
import co.cask.cdap.messaging.store.MessageTableTest;
import co.cask.cdap.messaging.store.MetadataTable;
import co.cask.cdap.messaging.store.TableFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/**
 * Tests for {@link LevelDBMessageTable}.
 */
public class LevelDBMessageTableTest extends MessageTableTest {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  protected static CConfiguration cConf;
  private static TableFactory tableFactory;

  @BeforeClass
  public static void init() throws IOException {
    cConf = CConfiguration.create();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, tmpFolder.newFolder().getAbsolutePath());
    tableFactory = new LevelDBTableFactory(cConf);
  }

  @Override
  protected MessageTable getMessageTable() throws Exception {
    return tableFactory.createMessageTable("message");
  }

  @Override
  protected MetadataTable getMetadataTable() throws Exception {
    return tableFactory.createMetadataTable("metadata");
  }
}
