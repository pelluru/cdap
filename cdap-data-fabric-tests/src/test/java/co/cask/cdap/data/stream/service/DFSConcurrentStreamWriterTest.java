/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.data.stream.service;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.namespace.NamespacedLocationFactory;
import co.cask.cdap.common.namespace.NamespacedLocationFactoryTestClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.twill.filesystem.FileContextLocationFactory;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;

/**
 *
 */
public class DFSConcurrentStreamWriterTest extends ConcurrentStreamWriterTestBase {

  private static NamespacedLocationFactory namespacedLocationFactory;
  private static MiniDFSCluster dfsCluster;


  @BeforeClass
  public static void init() throws IOException {
    Configuration hConf = new Configuration();
    hConf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, TMP_FOLDER.newFolder().getAbsolutePath());
    dfsCluster = new MiniDFSCluster.Builder(hConf).numDataNodes(1).build();
    dfsCluster.waitClusterUp();
    LocationFactory locationFactory = new FileContextLocationFactory(dfsCluster.getFileSystem().getConf());
    namespacedLocationFactory = new NamespacedLocationFactoryTestClient(CConfiguration.create(), locationFactory);

  }

  @AfterClass
  public static void finish() {
    dfsCluster.shutdown();
  }

  @Override
  protected NamespacedLocationFactory getNamespacedLocationFactory() {
    return namespacedLocationFactory;
  }
}
