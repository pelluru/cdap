/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.logging.read;

import co.cask.cdap.api.Transactional;
import co.cask.cdap.api.dataset.DatasetManager;
import co.cask.cdap.api.metrics.MetricsCollectionService;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.NonCustomLocationUnitTestModule;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.common.metrics.NoOpMetricsCollectionService;
import co.cask.cdap.common.namespace.NamespaceQueryAdmin;
import co.cask.cdap.common.namespace.SimpleNamespaceQueryAdmin;
import co.cask.cdap.data.dataset.SystemDatasetInstantiator;
import co.cask.cdap.data.runtime.DataSetsModules;
import co.cask.cdap.data.runtime.SystemDatasetRuntimeModule;
import co.cask.cdap.data2.datafabric.dataset.DefaultDatasetManager;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.MultiThreadDatasetCache;
import co.cask.cdap.data2.transaction.Transactions;
import co.cask.cdap.logging.LoggingConfiguration;
import co.cask.cdap.logging.appender.system.CDAPLogAppender;
import co.cask.cdap.logging.appender.system.LogPathIdentifier;
import co.cask.cdap.logging.context.LoggingContextHelper;
import co.cask.cdap.logging.guice.LoggingModules;
import co.cask.cdap.logging.meta.FileMetaDataReader;
import co.cask.cdap.logging.meta.FileMetaDataWriter;
import co.cask.cdap.logging.write.FileMetaDataManager;
import co.cask.cdap.logging.write.LogLocation;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import co.cask.cdap.security.authorization.AuthorizationTestModule;
import co.cask.cdap.security.impersonation.DefaultOwnerAdmin;
import co.cask.cdap.security.impersonation.OwnerAdmin;
import co.cask.cdap.security.impersonation.UGIProvider;
import co.cask.cdap.security.impersonation.UnsupportedUGIProvider;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.tephra.RetryStrategies;
import org.apache.tephra.TransactionManager;
import org.apache.tephra.TransactionSystemClient;
import org.apache.tephra.runtime.TransactionModules;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

public class FileMetadataTest {
  @ClassRule
  public static final TemporaryFolder TMP_FOLDER = new TemporaryFolder();

  private static Injector injector;
  private static TransactionManager txManager;

  @BeforeClass
  public static void setUpContext() throws Exception {
    Configuration hConf = HBaseConfiguration.create();
    final CConfiguration cConf = CConfiguration.create();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, TMP_FOLDER.newFolder().getAbsolutePath());
    String logBaseDir = cConf.get(LoggingConfiguration.LOG_BASE_DIR) + "/" + CDAPLogAppender.class.getSimpleName();
    cConf.set(LoggingConfiguration.LOG_BASE_DIR, logBaseDir);

    injector = Guice.createInjector(
      new ConfigModule(cConf, hConf),
      new NonCustomLocationUnitTestModule().getModule(),
      new TransactionModules().getInMemoryModules(),
      new LoggingModules().getInMemoryModules(),
      new DataSetsModules().getInMemoryModules(),
      new SystemDatasetRuntimeModule().getInMemoryModules(),
      new AuthorizationTestModule(),
      new AuthorizationEnforcementModule().getInMemoryModules(),
      new AuthenticationContextModules().getNoOpModule(),
      new AbstractModule() {
        @Override
        protected void configure() {
          bind(MetricsCollectionService.class).to(NoOpMetricsCollectionService.class);
          bind(UGIProvider.class).to(UnsupportedUGIProvider.class);
          bind(OwnerAdmin.class).to(DefaultOwnerAdmin.class);
          bind(NamespaceQueryAdmin.class).to(SimpleNamespaceQueryAdmin.class);
        }
      }
    );

    txManager = injector.getInstance(TransactionManager.class);
    txManager.startAndWait();
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    txManager.stopAndWait();
  }

  @Test
  public void testFileMetadataReadWrite() throws Exception {
    DatasetFramework datasetFramework = injector.getInstance(DatasetFramework.class);
    DatasetManager datasetManager = new DefaultDatasetManager(datasetFramework, NamespaceId.SYSTEM,
                                                              co.cask.cdap.common.service.RetryStrategies.noRetry());
    Transactional transactional = Transactions.createTransactionalWithRetry(
      Transactions.createTransactional(new MultiThreadDatasetCache(
        new SystemDatasetInstantiator(datasetFramework), injector.getInstance(TransactionSystemClient.class),
        NamespaceId.SYSTEM, ImmutableMap.<String, String>of(), null, null)),
      RetryStrategies.retryOnConflict(20, 100)
    );

    FileMetaDataWriter fileMetaDataWriter = new FileMetaDataWriter(datasetManager, transactional);
    LogPathIdentifier logPathIdentifier =
      new LogPathIdentifier(NamespaceId.DEFAULT.getNamespace(), "testApp", "testFlow");
    LocationFactory locationFactory = injector.getInstance(LocationFactory.class);
    Location location = locationFactory.create(TMP_FOLDER.newFolder().getPath()).append("/logs");
    long currentTime = System.currentTimeMillis();
    for (int i = 10; i <= 100; i += 10) {
      // i is the event time
      fileMetaDataWriter.writeMetaData(logPathIdentifier, i, currentTime,
                                       location.append(Integer.toString(i)));
    }

    // for the timestamp 80, add new new log path id with different current time.

    fileMetaDataWriter.writeMetaData(logPathIdentifier, 80, currentTime + 1,
                                     location.append("81"));

    fileMetaDataWriter.writeMetaData(logPathIdentifier, 80, currentTime + 2,
                                     location.append("82"));

    // reader test
    FileMetaDataReader fileMetadataReader = injector.getInstance(FileMetaDataReader.class);

    Assert.assertEquals(12, fileMetadataReader.listFiles(logPathIdentifier, 0, 100).size());
    Assert.assertEquals(5, fileMetadataReader.listFiles(logPathIdentifier, 20, 50).size());
    Assert.assertEquals(2, fileMetadataReader.listFiles(logPathIdentifier, 100, 150).size());

    // should include the latest file with event start time 80.
    List<LogLocation> locationList = fileMetadataReader.listFiles(logPathIdentifier, 81, 85);
    Assert.assertEquals(1, locationList.size());
    Assert.assertEquals(80, locationList.get(0).getEventTimeMs());
    Assert.assertEquals(location.append("82"), locationList.get(0).getLocation());

    Assert.assertEquals(1, fileMetadataReader.listFiles(logPathIdentifier, 150, 1000).size());
  }

  @Test
  public void testFileMetadataReadWriteAcrossFormats() throws Exception {
    DatasetFramework datasetFramework = injector.getInstance(DatasetFramework.class);
    DatasetManager datasetManager = new DefaultDatasetManager(datasetFramework, NamespaceId.SYSTEM,
                                                              co.cask.cdap.common.service.RetryStrategies.noRetry());
    Transactional transactional = Transactions.createTransactionalWithRetry(
      Transactions.createTransactional(new MultiThreadDatasetCache(
        new SystemDatasetInstantiator(datasetFramework), injector.getInstance(TransactionSystemClient.class),
        NamespaceId.SYSTEM, ImmutableMap.<String, String>of(), null, null)),
      RetryStrategies.retryOnConflict(20, 100)
    );
    FileMetaDataManager fileMetaDataManager = injector.getInstance(FileMetaDataManager.class);
    FileMetaDataWriter fileMetaDataWriter = new FileMetaDataWriter(datasetManager, transactional);
    LogPathIdentifier logPathIdentifier =
      new LogPathIdentifier(NamespaceId.DEFAULT.getNamespace(), "testApp", "testFlow");
    LocationFactory locationFactory = injector.getInstance(LocationFactory.class);
    Location location = locationFactory.create(TMP_FOLDER.newFolder().getPath()).append("/logs");
    long currentTime = System.currentTimeMillis();

    LoggingContext loggingContext =
      LoggingContextHelper.getLoggingContext(NamespaceId.DEFAULT.getNamespace(), "testApp", "testFlow");

    // 10 files in old format
    for (int i = 1; i <= 10; i++) {
      // i is the event time
      fileMetaDataManager.writeMetaData(loggingContext, currentTime + i,
                                       location.append("testFile" + Integer.toString(i)));
    }

    long eventTime = currentTime + 20;
    long newCurrentTime = currentTime + 100;
    // 10 files in new format
    for (int i = 1; i <= 10; i++) {
      fileMetaDataWriter.writeMetaData(logPathIdentifier, eventTime + i, newCurrentTime + i,
                                       location.append("testFileNew" + Integer.toString(i)));
    }
    // reader test
    FileMetaDataReader fileMetadataReader = injector.getInstance(FileMetaDataReader.class);
    // scan only in old files time range
    List<LogLocation> locations = fileMetadataReader.listFiles(logPathIdentifier, currentTime + 2, currentTime + 6);
    // should include files from currentTime (1..6)
    Assert.assertEquals(6, locations.size());
    for (LogLocation logLocation : locations) {
      Assert.assertEquals(LogLocation.VERSION_0, logLocation.getFrameworkVersion());
    }

    // scan only in new files time range
    locations = fileMetadataReader.listFiles(logPathIdentifier, eventTime + 2, eventTime + 6);
    // should include files from currentTime (1..6)
    Assert.assertEquals(6, locations.size());
    for (LogLocation logLocation : locations) {
      Assert.assertEquals(LogLocation.VERSION_1, logLocation.getFrameworkVersion());
    }

    // scan time range across formats
    locations = fileMetadataReader.listFiles(logPathIdentifier, currentTime + 2, eventTime + 6);
    // should include files from old range (1..10) and new range (1..6)
    Assert.assertEquals(16, locations.size());

    for (int i = 0; i < locations.size(); i++) {
      if (i < 10) {
        Assert.assertEquals(LogLocation.VERSION_0, locations.get(i).getFrameworkVersion());
        Assert.assertEquals(location.append("testFile" + Integer.toString(i + 1)), locations.get(i).getLocation());
      } else {
        Assert.assertEquals(LogLocation.VERSION_1, locations.get(i).getFrameworkVersion());
        Assert.assertEquals(location.append("testFileNew" + Integer.toString(i - 9)), locations.get(i).getLocation());
      }
    }
  }
}
