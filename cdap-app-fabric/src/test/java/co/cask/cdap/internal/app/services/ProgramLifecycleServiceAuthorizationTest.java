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

package co.cask.cdap.internal.app.services;

import co.cask.cdap.AllProgramsApp;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.test.AppJarHelper;
import co.cask.cdap.internal.AppFabricTestHelper;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.InstanceId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.security.Action;
import co.cask.cdap.proto.security.Principal;
import co.cask.cdap.security.authorization.AuthorizerInstantiator;
import co.cask.cdap.security.authorization.InMemoryAuthorizer;
import co.cask.cdap.security.spi.authentication.SecurityRequestContext;
import co.cask.cdap.security.spi.authorization.Authorizer;
import com.google.inject.Injector;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Test authorization for ProgramLifeCycleService
 */
public class ProgramLifecycleServiceAuthorizationTest {

  @ClassRule
  public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();
  private static final Principal ALICE = new Principal("alice", Principal.PrincipalType.USER);

  private static CConfiguration cConf;
  private static Authorizer authorizer;
  private static AppFabricServer appFabricServer;
  private static ProgramLifecycleService programLifecycleService;

  @BeforeClass
  public static void setup() throws Exception {
    cConf = createCConf();
    Injector injector = AppFabricTestHelper.getInjector(cConf);
    authorizer = injector.getInstance(AuthorizerInstantiator.class).get();
    appFabricServer = injector.getInstance(AppFabricServer.class);
    appFabricServer.startAndWait();
    programLifecycleService = injector.getInstance(ProgramLifecycleService.class);
  }

  @Test
  public void testProgramList() throws Exception {
    SecurityRequestContext.setUserId(ALICE.getName());
    // AppFabricTestHelper tries to create a namespace if it does not already exist
    authorizer.grant(new InstanceId(cConf.get(Constants.INSTANCE_NAME)), ALICE, Collections.singleton(Action.ADMIN));
    authorizer.grant(NamespaceId.DEFAULT, ALICE, Collections.singleton(Action.WRITE));
    AppFabricTestHelper.deployApplication(Id.Namespace.DEFAULT, AllProgramsApp.class, null, cConf);
    for (ProgramType type : ProgramType.values()) {
      if (!type.equals(ProgramType.CUSTOM_ACTION) && !type.equals(ProgramType.WEBAPP)) {
        Assert.assertFalse(
          programLifecycleService.list(NamespaceId.DEFAULT, type).isEmpty()
        );
        SecurityRequestContext.setUserId("bob");
        Assert.assertTrue(programLifecycleService.list(NamespaceId.DEFAULT, type).isEmpty());
        SecurityRequestContext.setUserId("alice");
      }
    }
  }

  @AfterClass
  public static void tearDown() {
    appFabricServer.stopAndWait();
  }

  private static CConfiguration createCConf() throws IOException {
    CConfiguration cConf = CConfiguration.create();
    cConf.setBoolean(Constants.Security.ENABLED, true);
    cConf.setBoolean(Constants.Security.Authorization.ENABLED, true);
    // we only want to test authorization, but we don't specify principal/keytab, so disable kerberos
    cConf.setBoolean(Constants.Security.KERBEROS_ENABLED, false);
    cConf.setInt(Constants.Security.Authorization.CACHE_MAX_ENTRIES, 0);
    LocationFactory locationFactory = new LocalLocationFactory(new File(TEMPORARY_FOLDER.newFolder().toURI()));
    Location authorizerJar = AppJarHelper.createDeploymentJar(locationFactory, InMemoryAuthorizer.class);
    cConf.set(Constants.Security.Authorization.EXTENSION_JAR_PATH, authorizerJar.toURI().getPath());
    return cConf;
  }
}
