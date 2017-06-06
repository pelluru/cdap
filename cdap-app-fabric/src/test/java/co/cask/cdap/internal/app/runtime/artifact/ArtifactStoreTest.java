/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.artifact;

import co.cask.cdap.WordCountApp;
import co.cask.cdap.api.artifact.ApplicationClass;
import co.cask.cdap.api.artifact.ArtifactClasses;
import co.cask.cdap.api.artifact.ArtifactRange;
import co.cask.cdap.api.artifact.ArtifactScope;
import co.cask.cdap.api.artifact.ArtifactVersion;
import co.cask.cdap.api.artifact.ArtifactVersionRange;
import co.cask.cdap.api.artifact.InvalidArtifactRangeException;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.plugin.PluginClass;
import co.cask.cdap.api.plugin.PluginPropertyField;
import co.cask.cdap.common.ArtifactAlreadyExistsException;
import co.cask.cdap.common.ArtifactNotFoundException;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.internal.AppFabricTestHelper;
import co.cask.cdap.internal.app.runtime.artifact.app.inspection.InspectionApp;
import co.cask.cdap.internal.app.runtime.plugin.PluginNotExistsException;
import co.cask.cdap.internal.io.ReflectionSchemaGenerator;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.artifact.ArtifactSortOrder;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.Ids;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.security.impersonation.DefaultImpersonator;
import co.cask.cdap.security.impersonation.EntityImpersonator;
import co.cask.cdap.test.SlowTests;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import org.apache.twill.filesystem.Location;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 */
// suppressing warnings for Bytes.toBytes() when we know the result is not null
@SuppressWarnings("ConstantConditions")
public class ArtifactStoreTest {
  private static ArtifactStore artifactStore;

  @BeforeClass
  public static void setup() throws Exception {
    artifactStore = AppFabricTestHelper.getInjector().getInstance(ArtifactStore.class);
  }

  @After
  public void cleanup() throws IOException {
    artifactStore.clear(NamespaceId.DEFAULT);
  }

  @Test
  public void testGetNonexistantArtifact() throws IOException {
    NamespaceId namespace = Ids.namespace("ns1");

    // no artifacts in a namespace should return an empty collection
    Assert.assertTrue(artifactStore.getArtifacts(namespace).isEmpty());
    // no artifacts in range should return an empty collection
    ArtifactRange range = new ArtifactRange(
      namespace.getNamespace(), "something", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0"));
    Assert.assertTrue(artifactStore.getArtifacts(range, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED).isEmpty());

    // no artifact by namespace and artifact name should throw an exception
    try {
      artifactStore.getArtifacts(namespace, "something", Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }

    // no artifact by namespace, artifact name, and version should throw an exception
    try {
      artifactStore.getArtifact(Id.Artifact.from(namespace.toId(), "something", "1.0.0"));
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }
  }

  @Test
  public void testGetNonexistantPlugin() throws Exception {
    Id.Artifact parentArtifact = Id.Artifact.from(Id.Namespace.DEFAULT, "abc", "1.2.3");

    // if parent doesn't exist, we expect it to throw ArtifactNotFound
    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact);
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }

    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact, "sometype");
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }

    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact, "sometype", "somename", null,
                                     Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }

    // now add the parent artifact without any plugins
    ArtifactMeta meta = new ArtifactMeta(ArtifactClasses.builder().build());
    writeArtifact(parentArtifact, meta, "jar contents");

    // no plugins in a namespace should return an empty collection
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact).isEmpty());

    // no plugins in namespace of a given type should return an empty map
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact, "sometype").isEmpty());

    // no plugins in namespace of a given type and name should throw an exception about no plugins
    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifact, "sometype", "somename", null,
                                     Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
      Assert.fail();
    } catch (PluginNotExistsException e) {
      // expected
    }
  }

  @Test
  public void testAddGetSingleArtifact() throws Exception {
    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "myplugins", "1.0.0");
    PluginClass plugin1 =
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin2 =
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin3 =
      new PluginClass("btype", "plugin3", "", "c.c.c.plugin3", "cfg", ImmutableMap.<String, PluginPropertyField>of());

    Set<PluginClass> plugins = ImmutableSet.of(plugin1, plugin2, plugin3);
    ApplicationClass appClass = new ApplicationClass(
      InspectionApp.class.getName(), "",
      new ReflectionSchemaGenerator().generate(InspectionApp.AConfig.class));
    ArtifactMeta artifactMeta =
      new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).addApp(appClass).build());

    String artifactContents = "my artifact contents";
    writeArtifact(artifactId, artifactMeta, artifactContents);

    ArtifactDetail artifactDetail = artifactStore.getArtifact(artifactId);
    assertEqual(artifactId, artifactMeta, artifactContents, artifactDetail);

    // test that plugins in the artifact show up when getting plugins for that artifact
    Map<ArtifactDescriptor, Set<PluginClass>> pluginsMap =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, artifactId);
    Assert.assertEquals(1, pluginsMap.size());
    Assert.assertTrue(pluginsMap.containsKey(artifactDetail.getDescriptor()));
    Set<PluginClass> expected = ImmutableSet.copyOf(plugins);
    Set<PluginClass> actual = ImmutableSet.copyOf(pluginsMap.get(artifactDetail.getDescriptor()));
    Assert.assertEquals(expected, actual);

    // test plugins for the specific type
    pluginsMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, artifactId, "atype");
    Assert.assertEquals(1, pluginsMap.size());
    Assert.assertTrue(pluginsMap.containsKey(artifactDetail.getDescriptor()));
    expected = ImmutableSet.of(plugin1, plugin2);
    actual = ImmutableSet.copyOf(pluginsMap.get(artifactDetail.getDescriptor()));
    Assert.assertEquals(expected, actual);

    // test plugins for specific type and name
    Map<ArtifactDescriptor, PluginClass> pluginClasses =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT.getNamespaceId(),
                                     artifactId, "btype", "plugin3", null, Integer.MAX_VALUE,
                                     ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(1, pluginClasses.size());
    Assert.assertTrue(pluginClasses.containsKey(artifactDetail.getDescriptor()));
    Assert.assertEquals(plugin3, pluginClasses.get(artifactDetail.getDescriptor()));
  }

  @Test
  public void testDelete() throws Exception {
    // write an artifact with an app
    Id.Artifact parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    ApplicationClass appClass = new ApplicationClass(
      InspectionApp.class.getName(), "",
      new ReflectionSchemaGenerator().generate(InspectionApp.AConfig.class));
    ArtifactMeta artifactMeta =
      new ArtifactMeta(ArtifactClasses.builder().addApp(appClass).build());
    writeArtifact(parentId, artifactMeta, "parent contents");

    // write a child artifact that extends the parent with some plugins
    Id.Artifact childId = Id.Artifact.from(Id.Namespace.DEFAULT, "myplugins", "1.0.0");
    List<PluginClass> plugins = ImmutableList.of(
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of()),
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    Set<ArtifactRange> parents = ImmutableSet.of(new ArtifactRange(
      parentId.getNamespace().getId(), parentId.getName(),
      new ArtifactVersion("0.1.0"), new ArtifactVersion("2.0.0")));
    artifactMeta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parents);
    writeArtifact(childId, artifactMeta, "child contents");

    // check parent has plugins from the child
    Assert.assertFalse(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    // delete the child artifact
    artifactStore.delete(childId);

    // shouldn't be able to get artifact detail
    try {
      artifactStore.getArtifact(childId);
      Assert.fail();
    } catch (ArtifactNotFoundException e) {
      // expected
    }

    // shouldn't see it in the list
    List<ArtifactDetail> artifactList = artifactStore.getArtifacts(parentId.getNamespace().toEntityId());
    Assert.assertEquals(1, artifactList.size());
    Assert.assertEquals(parentId.getName(), artifactList.get(0).getDescriptor().getArtifactId().getName());
    // shouldn't see any more plugins for parent
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    // delete parent
    artifactStore.delete(parentId);
    // nothing should be in the list
    Assert.assertTrue(artifactStore.getArtifacts(parentId.getNamespace().toEntityId()).isEmpty());
    // shouldn't be able to see app class either
    Assert.assertTrue(artifactStore.getApplicationClasses(NamespaceId.DEFAULT, appClass.getClassName()).isEmpty());
  }

  @Test(expected = ArtifactAlreadyExistsException.class)
  public void testImmutability() throws Exception {
    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "myplugins", "1.0.0");
    ArtifactMeta artifactMeta = new ArtifactMeta(ArtifactClasses.builder().build());

    String artifactContents = "abc123";
    // this should work
    try {
      writeArtifact(artifactId, artifactMeta, artifactContents);
    } catch (ArtifactAlreadyExistsException e) {
      Assert.fail();
    }
    // this should throw an exception, since artifacts are immutable
    writeArtifact(artifactId, artifactMeta, artifactContents);
  }

  @Test
  public void testSnapshotMutability() throws Exception {
    // write parent
    Id.Artifact parentArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    ArtifactMeta parentMeta = new ArtifactMeta(ArtifactClasses.builder().build());
    writeArtifact(parentArtifactId, parentMeta, "content");

    ArtifactRange parentArtifacts = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0"));
    // write the snapshot once
    PluginClass plugin1 = new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg",
      ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin2 = new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg",
      ImmutableMap.<String, PluginPropertyField>of());
    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "myplugins", "1.0.0-SNAPSHOT");
    ArtifactMeta artifactMeta = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(plugin1, plugin2).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactId, artifactMeta, "abc123");

    // update meta and jar contents
    artifactMeta = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(plugin2).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactId, artifactMeta, "xyz321");

    // check the metadata and contents got updated
    ArtifactDetail detail = artifactStore.getArtifact(artifactId);
    assertEqual(artifactId, artifactMeta, "xyz321", detail);

    // check that plugin1 was deleted and plugin2 remains
    Assert.assertEquals(ImmutableMap.of(detail.getDescriptor(), plugin2),
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, plugin2.getType(), plugin2.getName(),
                                     null, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED));
    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, plugin1.getType(), plugin1.getName(),
                                     null, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
      Assert.fail();
    } catch (PluginNotExistsException e) {
      // expected
    }
  }

  @Test
  public void testNamespaceIsolation() throws Exception {
    Id.Namespace namespace1 = Id.Namespace.from("ns1");
    Id.Namespace namespace2 = Id.Namespace.from("ns2");
    Id.Artifact artifact1 = Id.Artifact.from(namespace1, "myplugins", "1.0.0");
    Id.Artifact artifact2 = Id.Artifact.from(namespace2, "myplugins", "1.0.0");
    String contents1 = "first contents";
    String contents2 = "second contents";
    PluginClass plugin1 =
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin2 =
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    ArtifactMeta meta1 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin1).build());
    ArtifactMeta meta2 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin2).build());

    writeArtifact(artifact1, meta1, contents1);
    writeArtifact(artifact2, meta2, contents2);

    try {
      ArtifactDetail info1 = artifactStore.getArtifact(artifact1);
      ArtifactDetail info2 = artifactStore.getArtifact(artifact2);

      assertEqual(artifact1, meta1, contents1, info1);
      assertEqual(artifact2, meta2, contents2, info2);

      List<ArtifactDetail> namespace1Artifacts = artifactStore.getArtifacts(namespace1.toEntityId());
      List<ArtifactDetail> namespace2Artifacts = artifactStore.getArtifacts(namespace2.toEntityId());
      Assert.assertEquals(1, namespace1Artifacts.size());
      assertEqual(artifact1, meta1, contents1, namespace1Artifacts.get(0));
      Assert.assertEquals(1, namespace2Artifacts.size());
      assertEqual(artifact2, meta2, contents2, namespace2Artifacts.get(0));
    } finally {
      artifactStore.clear(namespace1.toEntityId());
      artifactStore.clear(namespace2.toEntityId());
    }
  }

  @Test
  public void testPluginNamespaceIsolation() throws Exception {
    // write some system artifact
    Id.Artifact systemAppArtifact = Id.Artifact.from(Id.Namespace.SYSTEM, "app", "1.0.0");
    ArtifactMeta systemAppMeta = new ArtifactMeta(
      ArtifactClasses.builder().addApp(new ApplicationClass("co.cask.class", "desc", null)).build());
    writeArtifact(systemAppArtifact, systemAppMeta, "app contents");
    Set<ArtifactRange> usableBy = ImmutableSet.of(
      new ArtifactRange(systemAppArtifact.getNamespace().getId(), systemAppArtifact.getName(),
                        systemAppArtifact.getVersion(), true, systemAppArtifact.getVersion(), true));

    PluginClass plugin =
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    // write a plugins artifact in namespace1
    NamespaceId namespace1 = Ids.namespace("ns1");
    Id.Artifact artifact1 = Id.Artifact.from(namespace1.toId(), "plugins1", "1.0.0");
    ArtifactMeta meta1 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin).build(), usableBy);
    String contents1 = "plugin1 contents";
    writeArtifact(artifact1, meta1, contents1);

    // write a plugins artifact in namespace2
    NamespaceId namespace2 = Ids.namespace("ns2");
    Id.Artifact artifact2 = Id.Artifact.from(namespace2.toId(), "plugins2", "1.0.0");
    ArtifactMeta meta2 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin).build(), usableBy);
    String contents2 = "plugin2 contents";
    writeArtifact(artifact2, meta2, contents2);

    try {
      // this should only get plugins from artifact1
      SortedMap<ArtifactDescriptor, Set<PluginClass>> plugins =
        artifactStore.getPluginClasses(namespace1, systemAppArtifact);
      Assert.assertEquals(1, plugins.size());
      ArtifactDescriptor artifactDescriptor = plugins.firstKey();
      Assert.assertEquals(artifact1.toArtifactId(), artifactDescriptor.getArtifactId());
      assertContentsEqual(contents1, artifactDescriptor.getLocation());

      // this should only get plugins from artifact2
      plugins = artifactStore.getPluginClasses(namespace2, systemAppArtifact);
      Assert.assertEquals(1, plugins.size());
      artifactDescriptor = plugins.firstKey();
      Assert.assertEquals(artifact2.toArtifactId(), artifactDescriptor.getArtifactId());
      assertContentsEqual(contents2, artifactDescriptor.getLocation());
    } finally {
      artifactStore.clear(namespace1);
      artifactStore.clear(namespace2);
      artifactStore.clear(NamespaceId.SYSTEM);
    }
  }

  @Test
  public void testGetArtifacts() throws Exception {
    // add 1 version of another artifact1
    Id.Artifact artifact1V1 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifact1", "1.0.0");
    String contents1V1 = "first contents v1";
    PluginClass plugin1V1 =
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    ArtifactMeta meta1V1 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin1V1).build());
    writeArtifact(artifact1V1, meta1V1, contents1V1);

    // add 2 versions of an artifact2
    Id.Artifact artifact2V1 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifact2", "0.1.0");
    Id.Artifact artifact2V2 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifact2", "0.1.1");
    Id.Artifact artifact2V3 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifact2", "0.1.1-SNAPSHOT");
    String contents2V1 = "second contents v1";
    String contents2V2 = "second contents v2";
    String contents2V3 = "second contents v3";
    PluginClass plugin2V1 =
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin2V2 =
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    PluginClass plugin2V3 =
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of());
    ArtifactMeta meta2V1 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin2V1).build());
    ArtifactMeta meta2V2 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin2V2).build());
    ArtifactMeta meta2V3 = new ArtifactMeta(ArtifactClasses.builder().addPlugin(plugin2V3).build());
    writeArtifact(artifact2V1, meta2V1, contents2V1);
    writeArtifact(artifact2V2, meta2V2, contents2V2);
    writeArtifact(artifact2V3, meta2V3, contents2V3);

    // test we get 1 version of artifact1 and 2 versions of artifact2
    List<ArtifactDetail> artifact1Versions =
      artifactStore.getArtifacts(artifact1V1.getNamespace().toEntityId(), artifact1V1.getName(), Integer.MAX_VALUE,
                                 ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(1, artifact1Versions.size());
    assertEqual(artifact1V1, meta1V1, contents1V1, artifact1Versions.get(0));

    List<ArtifactDetail> artifact2Versions =
      artifactStore.getArtifacts(artifact2V1.getNamespace().toEntityId(), artifact2V1.getName(), Integer.MAX_VALUE,
                                 ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(3, artifact2Versions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifact2Versions.get(0));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifact2Versions.get(1));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifact2Versions.get(2));

    // test get 2 versions of artifact 2
    artifact2Versions = artifactStore.getArtifacts(artifact2V1.getNamespace().toEntityId(), artifact2V1.getName(),
                                                   2, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(2, artifact2Versions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifact2Versions.get(0));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifact2Versions.get(1));

    // test get sorted version of artifact 2
    artifact2Versions = artifactStore.getArtifacts(artifact2V1.getNamespace().toEntityId(), artifact2V1.getName(),
                                                   3, ArtifactSortOrder.DESC);
    Assert.assertEquals(3, artifact2Versions.size());
    assertEqual(artifact2V2, meta2V2, contents2V2, artifact2Versions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifact2Versions.get(1));
    assertEqual(artifact2V1, meta2V1, contents2V1, artifact2Versions.get(2));

    // test get sorted and limited version of artifact 2
    artifact2Versions = artifactStore.getArtifacts(artifact2V1.getNamespace().toEntityId(), artifact2V1.getName(),
                                                   2, ArtifactSortOrder.DESC);
    Assert.assertEquals(2, artifact2Versions.size());
    assertEqual(artifact2V2, meta2V2, contents2V2, artifact2Versions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifact2Versions.get(1));

    artifact2Versions = artifactStore.getArtifacts(artifact2V1.getNamespace().toEntityId(), artifact2V1.getName(),
                                                   3, ArtifactSortOrder.ASC);
    Assert.assertEquals(3, artifact2Versions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifact2Versions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifact2Versions.get(1));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifact2Versions.get(2));

    // test we get all 3 in the getArtifactSummaries() call for the namespace
    List<ArtifactDetail> artifactVersions = artifactStore.getArtifacts(NamespaceId.DEFAULT);
    Assert.assertEquals(4, artifactVersions.size());
    assertEqual(artifact1V1, meta1V1, contents1V1, artifactVersions.get(0));
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(1));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifactVersions.get(2));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifactVersions.get(3));

    // test get using a range
    // this range should get everything
    ArtifactRange range = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "artifact2", new ArtifactVersion("0.1.0"), new ArtifactVersion("0.1.2"));
    artifactVersions = artifactStore.getArtifacts(range, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(3, artifactVersions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(0));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifactVersions.get(1));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifactVersions.get(2));
    // test get one version
    artifactVersions = artifactStore.getArtifacts(range, 1, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(1, artifactVersions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(0));
    // test get sorted versions
    artifactVersions = artifactStore.getArtifacts(range, 3, ArtifactSortOrder.DESC);
    Assert.assertEquals(3, artifact2Versions.size());
    assertEqual(artifact2V2, meta2V2, contents2V2, artifactVersions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifactVersions.get(1));
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(2));

    artifactVersions = artifactStore.getArtifacts(range, 3, ArtifactSortOrder.ASC);
    Assert.assertEquals(3, artifact2Versions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifactVersions.get(1));
    assertEqual(artifact2V2, meta2V2, contents2V2, artifactVersions.get(2));


    // this range should get just v0.1.1
    range = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "artifact2", new ArtifactVersion("0.1.1"), new ArtifactVersion("1.0.0"));
    artifactVersions = artifactStore.getArtifacts(range, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(1, artifactVersions.size());
    assertEqual(artifact2V2, meta2V2, contents2V2, artifactVersions.get(0));
    // this range should get just v0.1.0 and v0.1.1-SNAPSHOT
    range = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "artifact2", new ArtifactVersion("0.0.0"), new ArtifactVersion("0.1.1"));
    artifactVersions = artifactStore.getArtifacts(range, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(2, artifactVersions.size());
    assertEqual(artifact2V1, meta2V1, contents2V1, artifactVersions.get(0));
    assertEqual(artifact2V3, meta2V3, contents2V3, artifactVersions.get(1));
  }

  @Test
  public void testGetAppClasses() throws Exception {
    // create 2 versions of the same artifact with the same app class
    Id.Artifact app1v1Id = Id.Artifact.from(Id.Namespace.DEFAULT, "appA", "1.0.0");
    ApplicationClass inspectionClass1 = new ApplicationClass(
      InspectionApp.class.getName(), "v1",
      new ReflectionSchemaGenerator().generate(InspectionApp.AConfig.class));
    ArtifactMeta artifactMeta =
      new ArtifactMeta(ArtifactClasses.builder().addApp(inspectionClass1).build());
    writeArtifact(app1v1Id, artifactMeta, "my artifact contents");
    ArtifactDetail app1v1Detail = artifactStore.getArtifact(app1v1Id);

    Id.Artifact app1v2Id = Id.Artifact.from(Id.Namespace.DEFAULT, "appA", "2.0.0");
    ApplicationClass inspectionClass2 = new ApplicationClass(
      InspectionApp.class.getName(), "v2",
      new ReflectionSchemaGenerator().generate(InspectionApp.AConfig.class));
    artifactMeta = new ArtifactMeta(ArtifactClasses.builder().addApp(inspectionClass2).build());
    writeArtifact(app1v2Id, artifactMeta, "my artifact contents");
    ArtifactDetail app1v2Detail = artifactStore.getArtifact(app1v2Id);

    // create a different artifact with the same app class
    Id.Artifact app2v1Id = Id.Artifact.from(Id.Namespace.DEFAULT, "appB", "1.0.0");
    artifactMeta = new ArtifactMeta(ArtifactClasses.builder().addApp(inspectionClass1).build());
    writeArtifact(app2v1Id, artifactMeta, "other contents");
    ArtifactDetail app2v1Detail = artifactStore.getArtifact(app2v1Id);

    // create another artifact with a different app class
    Id.Artifact app3v1Id = Id.Artifact.from(Id.Namespace.DEFAULT, "appC", "1.0.0");
    ApplicationClass wordCountClass1 = new ApplicationClass(
      WordCountApp.class.getName(), "v1",
      new ReflectionSchemaGenerator().generate(InspectionApp.AConfig.class));
    artifactMeta = new ArtifactMeta(ArtifactClasses.builder().addApp(wordCountClass1).build());
    writeArtifact(app3v1Id, artifactMeta, "wc contents");
    ArtifactDetail app3v1Detail = artifactStore.getArtifact(app3v1Id);

    // test getting all app classes in the namespace
    Map<ArtifactDescriptor, List<ApplicationClass>> appClasses =
      artifactStore.getApplicationClasses(NamespaceId.DEFAULT);
    Map<ArtifactDescriptor, List<ApplicationClass>> expected =
      ImmutableMap.<ArtifactDescriptor, List<ApplicationClass>>of(
        app1v1Detail.getDescriptor(), ImmutableList.of(inspectionClass1),
        app1v2Detail.getDescriptor(), ImmutableList.of(inspectionClass2),
        app2v1Detail.getDescriptor(), ImmutableList.of(inspectionClass1),
        app3v1Detail.getDescriptor(), ImmutableList.of(wordCountClass1)
      );
    Assert.assertEquals(expected, appClasses);

    // test getting all app classes by class name
    Map<ArtifactDescriptor, ApplicationClass> appArtifacts =
      artifactStore.getApplicationClasses(NamespaceId.DEFAULT, InspectionApp.class.getName());
    Map<ArtifactDescriptor, ApplicationClass> expectedAppArtifacts = ImmutableMap.of(
      app1v1Detail.getDescriptor(), inspectionClass1,
      app1v2Detail.getDescriptor(), inspectionClass2,
      app2v1Detail.getDescriptor(), inspectionClass1
    );
    Assert.assertEquals(expectedAppArtifacts, appArtifacts);

    appArtifacts = artifactStore.getApplicationClasses(NamespaceId.DEFAULT, WordCountApp.class.getName());
    expectedAppArtifacts = ImmutableMap.of(app3v1Detail.getDescriptor(), wordCountClass1);
    Assert.assertEquals(expectedAppArtifacts, appArtifacts);

    Assert.assertTrue(artifactStore.getApplicationClasses(Ids.namespace("ghost")).isEmpty());
  }

  @Test
  public void testGetPlugins() throws Exception {
    ArtifactRange parentArtifacts = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0"));
    // we have 2 plugins of type A and 2 plugins of type B
    PluginClass pluginA1 = new PluginClass(
      "A", "p1", "desc", "c.p1", "cfg",
      ImmutableMap.of(
        "threshold", new PluginPropertyField("thresh", "description", "double", true, false),
        "retry", new PluginPropertyField("retries", "description", "int", false, false)
      )
    );
    PluginClass pluginA2 = new PluginClass(
      "A", "p2", "desc", "c.p2", "conf",
      ImmutableMap.of(
        "stream", new PluginPropertyField("stream", "description", "string", true, false)
      )
    );
    PluginClass pluginB1 = new PluginClass(
      "B", "p1", "desc", "c.p1", "cfg",
      ImmutableMap.of(
        "createIfNotExist", new PluginPropertyField("createIfNotExist", "desc", "boolean", false, false)
      )
    );
    PluginClass pluginB2 = new PluginClass(
      "B", "p2", "desc", "c.p2", "stuff",
      ImmutableMap.of(
        "numer", new PluginPropertyField("numerator", "description", "double", true, false),
        "denom", new PluginPropertyField("denominator", "description", "double", true, false)
      )
    );

    // add artifacts

    // not interested in artifact contents for this test, using some dummy value
    String contents = "0";

    // write parent
    Id.Artifact parentArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    ArtifactMeta parentMeta = new ArtifactMeta(ArtifactClasses.builder().build());
    writeArtifact(parentArtifactId, parentMeta, contents);

    // artifact artifactX-1.0.0 contains plugin A1
    Id.Artifact artifactXv100 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "1.0.0");
    ArtifactMeta metaXv100 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginA1).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactXv100, metaXv100, contents);
    ArtifactDescriptor artifactXv100Info = artifactStore.getArtifact(artifactXv100).getDescriptor();

    // artifact artifactX-1.1.0 contains plugin A1
    Id.Artifact artifactXv110 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "1.1.0");
    ArtifactMeta metaXv110 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginA1).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactXv110, metaXv110, contents);
    ArtifactDescriptor artifactXv110Info = artifactStore.getArtifact(artifactXv110).getDescriptor();

    // artifact artifactX-2.0.0 contains plugins A1 and A2
    Id.Artifact artifactXv200 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "2.0.0");
    ArtifactMeta metaXv200 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginA2).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactXv200, metaXv200, contents);
    ArtifactDescriptor artifactXv200Info = artifactStore.getArtifact(artifactXv200).getDescriptor();

    // artifact artifactY-1.0.0 contains plugin B1
    Id.Artifact artifactYv100 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactY", "1.0.0");
    ArtifactMeta metaYv100 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginB1).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactYv100, metaYv100, contents);
    ArtifactDescriptor artifactYv100Info = artifactStore.getArtifact(artifactYv100).getDescriptor();

    // artifact artifactY-2.0.0 contains plugin B2
    Id.Artifact artifactYv200 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactY", "2.0.0");
    ArtifactMeta metaYv200 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginB2).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactYv200, metaYv200, contents);
    ArtifactDescriptor artifactYv200Info = artifactStore.getArtifact(artifactYv200).getDescriptor();

    // artifact artifactZ-1.0.0 contains plugins A1 and B1
    Id.Artifact artifactZv100 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactZ", "1.0.0");
    ArtifactMeta metaZv100 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginB1).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactZv100, metaZv100, contents);
    ArtifactDescriptor artifactZv100Info = artifactStore.getArtifact(artifactZv100).getDescriptor();

    // artifact artifactZ-2.0.0 contains plugins A1, A2, B1, and B2
    Id.Artifact artifactZv200 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactZ", "2.0.0");
    ArtifactMeta metaZv200 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginA2, pluginB1, pluginB2).build(),
      ImmutableSet.of(parentArtifacts));
    writeArtifact(artifactZv200, metaZv200, contents);
    ArtifactDescriptor artifactZv200Info = artifactStore.getArtifact(artifactZv200).getDescriptor();

    // test getting all plugins in the namespace
    Map<ArtifactDescriptor, Set<PluginClass>> expected = Maps.newHashMap();
    expected.put(artifactXv100Info, ImmutableSet.of(pluginA1));
    expected.put(artifactXv110Info, ImmutableSet.of(pluginA1));
    expected.put(artifactXv200Info, ImmutableSet.of(pluginA1, pluginA2));
    expected.put(artifactYv100Info, ImmutableSet.of(pluginB1));
    expected.put(artifactYv200Info, ImmutableSet.of(pluginB2));
    expected.put(artifactZv100Info, ImmutableSet.of(pluginA1, pluginB1));
    expected.put(artifactZv200Info, ImmutableSet.of(pluginA1, pluginA2, pluginB1, pluginB2));
    Map<ArtifactDescriptor, Set<PluginClass>> actual =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId);
    Assert.assertEquals(expected, actual);

    // test getting all plugins by namespace and type
    // get all of type A
    expected = Maps.newHashMap();
    expected.put(artifactXv100Info, ImmutableSet.of(pluginA1));
    expected.put(artifactXv110Info, ImmutableSet.of(pluginA1));
    expected.put(artifactXv200Info, ImmutableSet.of(pluginA1, pluginA2));
    expected.put(artifactZv100Info, ImmutableSet.of(pluginA1));
    expected.put(artifactZv200Info, ImmutableSet.of(pluginA1, pluginA2));
    actual = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A");
    Assert.assertEquals(expected, actual);
    // get all of type B
    expected = Maps.newHashMap();
    expected.put(artifactYv100Info, ImmutableSet.of(pluginB1));
    expected.put(artifactYv200Info, ImmutableSet.of(pluginB2));
    expected.put(artifactZv100Info, ImmutableSet.of(pluginB1));
    expected.put(artifactZv200Info, ImmutableSet.of(pluginB1, pluginB2));
    actual = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "B");
    Assert.assertEquals(expected, actual);

    // test getting plugins by namespace, type, and name
    // get all of type A and name p1
    Map<ArtifactDescriptor, PluginClass> expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv100Info, pluginA1);
    expectedMap.put(artifactXv110Info, pluginA1);
    expectedMap.put(artifactXv200Info, pluginA1);
    expectedMap.put(artifactZv100Info, pluginA1);
    expectedMap.put(artifactZv200Info, pluginA1);
    Map<ArtifactDescriptor, PluginClass> actualMap =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", null, Integer.MAX_VALUE,
                                     ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(expectedMap, actualMap);

    // test get limited number
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", null, 1,
                                               ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(ImmutableMap.of(artifactXv100Info, pluginA1), actualMap);

    // test get DESC order
    actualMap = new TreeMap<>(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", null,
                                                             Integer.MAX_VALUE, ArtifactSortOrder.DESC));
    Assert.assertEquals(expectedMap, new TreeMap<>(actualMap).descendingMap());

    // test Predicate
    Predicate<ArtifactId> predicate = new Predicate<ArtifactId>() {
      @Override
      public boolean apply(ArtifactId input) {
        try {
          return input.getParent().equals(NamespaceId.DEFAULT) && input.getArtifact().equals("artifactX")
            && ArtifactVersionRange.parse("[1.0.0, 1.1.0)").versionIsInRange(new ArtifactVersion(input.getVersion()));
        } catch (InvalidArtifactRangeException e) {
          return false;
        }
      }
    };
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv100Info, pluginA1);
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", predicate,
                                               Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(expectedMap, actualMap);

    // test limit and order combined
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", null,
                                               1, ArtifactSortOrder.DESC);
    Assert.assertEquals(ImmutableMap.of(artifactZv200Info, pluginA1), actualMap);

    // test limit, order, predicate combined
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p1", predicate,
                                               1, ArtifactSortOrder.DESC);
    Assert.assertEquals(ImmutableMap.of(artifactXv100Info, pluginA1), actualMap);

    // get all of type A and name p2
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv200Info, pluginA2);
    expectedMap.put(artifactZv200Info, pluginA2);
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "A", "p2", null,
                                               Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(expectedMap, actualMap);
    // get all of type B and name p1
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactYv100Info, pluginB1);
    expectedMap.put(artifactZv100Info, pluginB1);
    expectedMap.put(artifactZv200Info, pluginB1);
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "B", "p1", null,
                                               Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(expectedMap, actualMap);
    // get all of type B and name p2
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactYv200Info, pluginB2);
    expectedMap.put(artifactZv200Info, pluginB2);
    actualMap = artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "B", "p2", null,
                                               Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED);
    Assert.assertEquals(expectedMap, actualMap);
  }

  @Test
  public void testGetPluginsByParentArtifactRanges() throws Exception {
    ArtifactRange parentArtifacts1 = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent1", new ArtifactVersion("1.0.0"), new ArtifactVersion("5.0.0"));

    // we have 2 plugins of type A and 2 plugins of type B
    PluginClass pluginA1 = new PluginClass(
      "A", "p1", "desc", "c.p1", "cfg",
      ImmutableMap.of(
        "threshold", new PluginPropertyField("thresh", "description", "double", true, false),
        "retry", new PluginPropertyField("retries", "description", "int", false, false)
      )
    );
    PluginClass pluginA2 = new PluginClass(
      "A", "p2", "desc", "c.p2", "conf",
      ImmutableMap.of(
        "stream", new PluginPropertyField("stream", "description", "string", true, false)
      )
    );

    // add artifacts

    // not interested in artifact contents for this test, using some dummy value
    String contents = "0";

    // write parent artifacts
    List<String> parentArtifactsVersions = ImmutableList.of("1.0.0", "1.2.1", "2.0.0", "3.0.0", "4.0.0");
    for (String artifactVersion : parentArtifactsVersions) {
      Id.Artifact parentArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent1", artifactVersion);
      ArtifactMeta parentMeta = new ArtifactMeta(ArtifactClasses.builder().build());
      writeArtifact(parentArtifactId, parentMeta, contents);
    }

    // artifact artifactX-1.0.0 contains plugin A1
    Id.Artifact artifactXv100 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "1.0.0");
    ArtifactMeta metaXv100 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginA1).build(), ImmutableSet.of(parentArtifacts1));
    writeArtifact(artifactXv100, metaXv100, contents);
    ArtifactDescriptor artifactXv100Info = artifactStore.getArtifact(artifactXv100).getDescriptor();

    // artifact artifactX-1.1.0 contains plugin A1
    Id.Artifact artifactXv110 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "1.1.0");
    ArtifactMeta metaXv110 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugin(pluginA1).build(), ImmutableSet.of(parentArtifacts1));
    writeArtifact(artifactXv110, metaXv110, contents);
    ArtifactDescriptor artifactXv110Info = artifactStore.getArtifact(artifactXv110).getDescriptor();

    // artifact artifactX-2.0.0 contains plugins A1 and A2
    Id.Artifact artifactXv200 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactX", "2.0.0");
    ArtifactMeta metaXv200 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginA2).build(), ImmutableSet.of(parentArtifacts1));
    writeArtifact(artifactXv200, metaXv200, contents);
    ArtifactDescriptor artifactXv200Info = artifactStore.getArtifact(artifactXv200).getDescriptor();

    ArtifactRange parentArtifactsrange1 = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent1", new ArtifactVersion("3.0.0"), new ArtifactVersion("5.0.0"));

    // artifact artifactZ-2.0.0 contains plugins A1, A2
    Id.Artifact artifactZv200 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactZ", "2.0.0");
    ArtifactMeta metaZv200 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginA2).build(),
      ImmutableSet.of(parentArtifactsrange1));
    writeArtifact(artifactZv200, metaZv200, contents);
    ArtifactDescriptor artifactZv200Info = artifactStore.getArtifact(artifactZv200).getDescriptor();

    // artifact written with this range should not come up as their parent range is out of the parent artifact range.
    ArtifactRange parentArtifactsOutOfRange1 = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent1", new ArtifactVersion("5.0.0"), new ArtifactVersion("8.0.0"));

    // artifact artifactZ-2.0.0 contains plugins A1, A2, B1, and B2
    Id.Artifact artifactZv300 = Id.Artifact.from(Id.Namespace.DEFAULT, "artifactZ", "3.0.0");
    ArtifactMeta metaZv300 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(pluginA1, pluginA2).build(),
      ImmutableSet.of(parentArtifactsOutOfRange1));
    writeArtifact(artifactZv300, metaZv300, contents);

    Map<ArtifactDescriptor, PluginClass> expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv100Info, pluginA1);
    expectedMap.put(artifactXv110Info, pluginA1);
    expectedMap.put(artifactXv200Info, pluginA1);
    expectedMap.put(artifactZv200Info, pluginA1);
    Assert.assertEquals(expectedMap, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifacts1, "A", "p1",
                                                                    null, Integer.MAX_VALUE,
                                                                    ArtifactSortOrder.UNORDERED));
    // test limited number
    Assert.assertEquals(ImmutableMap.of(artifactXv100Info, pluginA1),
                        artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifacts1, "A", "p1",
                                                       null, 1, ArtifactSortOrder.UNORDERED));
    // test DESC order
    Assert.assertEquals(expectedMap, new TreeMap<>(
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifacts1, "A", "p1", null, Integer.MAX_VALUE,
                                     ArtifactSortOrder.DESC)).descendingMap());

    ArtifactRange parentArtifactsSub1 = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent1", new ArtifactVersion("1.1.0"), new ArtifactVersion("2.0.0"));
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv100Info, pluginA1);
    expectedMap.put(artifactXv110Info, pluginA1);
    expectedMap.put(artifactXv200Info, pluginA1);

    //artifactZv200Info wont be here, as the parent range 3.0.0-5.0.0 for artifactZv200 plugin
    // wont match the 1.2.1 parent artifact version
    Assert.assertEquals(expectedMap, artifactStore.getPluginClasses(NamespaceId.DEFAULT,
                                                                    parentArtifactsSub1, "A", "p1", null,
                                                                    Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED));
    expectedMap = Maps.newHashMap();
    expectedMap.put(artifactXv200Info, pluginA2);
    expectedMap.put(artifactZv200Info, pluginA2);
    Assert.assertEquals(expectedMap, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifacts1, "A", "p2",
                                                                    null, Integer.MAX_VALUE,
                                                                    ArtifactSortOrder.UNORDERED));

    ArtifactRange parentArtifactsSub2 = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent1", new ArtifactVersion("5.0.0"), new ArtifactVersion("10.0.0"));
    try {
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactsSub2, "A", "p1", null, Integer.MAX_VALUE,
                                     ArtifactSortOrder.UNORDERED);
      Assert.fail("Get plugin class for invalid range should not retrun result");
    } catch (ArtifactNotFoundException e) {
      //no-op
    }
  }

  @Test
  public void testSamePluginDifferentArtifacts() throws Exception {
    ArtifactRange parentArtifacts = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0"));
    // add one artifact with a couple plugins
    Id.Artifact artifact1 = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins1", "1.0.0");
    Set<PluginClass> plugins = ImmutableSet.of(
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of()),
      new PluginClass("atype", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    ArtifactMeta meta1 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(plugins).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifact1, meta1, "something");
    ArtifactDescriptor artifact1Info = artifactStore.getArtifact(artifact1).getDescriptor();

    // add a different artifact with the same plugins
    Id.Artifact artifact2 = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins2", "1.0.0");
    ArtifactMeta meta2 = new ArtifactMeta(
      ArtifactClasses.builder().addPlugins(plugins).build(), ImmutableSet.of(parentArtifacts));
    writeArtifact(artifact2, meta2, "something");
    ArtifactDescriptor artifact2Info = artifactStore.getArtifact(artifact2).getDescriptor();

    Id.Artifact parentArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    writeArtifact(parentArtifactId, new ArtifactMeta(ArtifactClasses.builder().build()), "content");
    Map<ArtifactDescriptor, Set<PluginClass>> expected = Maps.newHashMap();
    expected.put(artifact1Info, plugins);
    expected.put(artifact2Info, plugins);
    Map<ArtifactDescriptor, Set<PluginClass>> actual =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testPluginParentInclusiveExclusiveVersions() throws Exception {
    // write artifacts that extend:
    // parent-[1.0.0,1.0.0] -- only visible by parent-1.0.0
    Id.Artifact id1  = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins", "0.0.1");
    Set<ArtifactRange> parentArtifacts = ImmutableSet.of(new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"),
      true, new ArtifactVersion("1.0.0"), true));
    List<PluginClass> plugins = ImmutableList.of(
      new PluginClass("typeA", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    ArtifactMeta meta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parentArtifacts);
    writeArtifact(id1, meta, "some contents");

    // parent-[2.0.0,2.0.1) -- only visible by parent-2.0.0
    Id.Artifact id2  = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins", "0.0.2");
    parentArtifacts = ImmutableSet.of(new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("2.0.0"), true,
      new ArtifactVersion("2.0.1"), false));
    plugins = ImmutableList.of(
      new PluginClass("typeA", "plugin2", "", "c.c.c.plugin2", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    meta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parentArtifacts);
    writeArtifact(id2, meta, "some contents");

    // parent-(3.0.0,3.0.1] -- only visible by parent-3.0.1
    Id.Artifact id3  = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins", "0.0.3");
    parentArtifacts = ImmutableSet.of(new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("3.0.0"), false,
      new ArtifactVersion("3.0.1"), true));
    plugins = ImmutableList.of(
      new PluginClass("typeA", "plugin3", "", "c.c.c.plugin3", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    meta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parentArtifacts);
    writeArtifact(id3, meta, "some contents");

    // parent-(4.0.0,4.0.2) -- only visible by parent-4.0.1
    Id.Artifact id4  = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins", "0.0.4");
    parentArtifacts = ImmutableSet.of(new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("4.0.0"), false,
      new ArtifactVersion("4.0.2"), false));
    plugins = ImmutableList.of(
      new PluginClass("typeA", "plugin4", "", "c.c.c.plugin4", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    meta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parentArtifacts);
    writeArtifact(id4, meta, "some contents");

    ArtifactMeta parentMeta = new ArtifactMeta(ArtifactClasses.builder().build());
    // check parent-1.0.0 has plugin1 but parent-0.0.9 does not and 1.0.1 does not
    Id.Artifact parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "0.0.9");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.1");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertEquals(1, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).size());

    // check parent-2.0.0 has plugin2 but parent-1.9.9 does not and 2.0.1 does not
    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.9.9");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "2.0.1");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "2.0.0");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertEquals(1, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).size());

    // check parent-3.0.1 has plugin3 but parent-3.0.0 does not and 3.0.2 does not
    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "3.0.0");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "3.0.2");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "3.0.1");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertEquals(1, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).size());

    // check parent-4.0.1 has plugin4 but parent-4.0.0 does not and 4.0.2 does not
    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "4.0.0");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "4.0.2");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).isEmpty());

    parentId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "4.0.1");
    writeArtifact(parentId, parentMeta, "content");
    Assert.assertEquals(1, artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentId).size());
  }

  // this test tests that when an artifact specifies a range of artifact versions it extends,
  // those versions are honored
  @Test
  public void testPluginParentVersions() throws Exception {
    // write an artifact that extends parent-[1.0.0, 2.0.0)
    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "plugins", "0.1.0");
    Set<ArtifactRange> parentArtifacts = ImmutableSet.of(new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0")));
    Set<PluginClass> plugins = ImmutableSet.of(
      new PluginClass("atype", "plugin1", "", "c.c.c.plugin1", "cfg", ImmutableMap.<String, PluginPropertyField>of())
    );
    ArtifactMeta meta = new ArtifactMeta(ArtifactClasses.builder().addPlugins(plugins).build(), parentArtifacts);
    writeArtifact(artifactId, meta, "some contents");
    ArtifactDescriptor artifactInfo = artifactStore.getArtifact(artifactId).getDescriptor();

    // check ids that are out of range. They should not return anything
    List<Id.Artifact> badIds = Lists.newArrayList(
      // ids that are too low
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "0.9.9"),
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0-SNAPSHOT"),
      // ids that are too high
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "2.0.0")
    );
    ArtifactMeta emptyMeta = new ArtifactMeta(ArtifactClasses.builder().build());
    for (Id.Artifact badId : badIds) {
      // write the parent artifact to make sure we don't get ArtifactNotFound exceptions with later calls
      // we're testing range filtering, not the absence of the parent artifact
      writeArtifact(badId, emptyMeta, "content");

      Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, badId).isEmpty());
      Assert.assertTrue(artifactStore.getPluginClasses(NamespaceId.DEFAULT, badId, "atype").isEmpty());
      try {
        artifactStore.getPluginClasses(NamespaceId.DEFAULT, badId, "atype", "plugin1", null, Integer.MAX_VALUE,
                                       ArtifactSortOrder.UNORDERED);
        Assert.fail();
      } catch (PluginNotExistsException e) {
        // expected
      }
    }

    // check ids that are in range return what we expect
    List<Id.Artifact> goodIds = Lists.newArrayList(
      // ids that are too low
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0"),
      // ids that are too high
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.9.9"),
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.99.999"),
      Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "2.0.0-SNAPSHOT")
    );
    Map<ArtifactDescriptor, Set<PluginClass>> expectedPluginsMapList = ImmutableMap.of(artifactInfo, plugins);
    Map<ArtifactDescriptor, PluginClass> expectedPluginsMap = ImmutableMap.of(artifactInfo, plugins.iterator().next());
    for (Id.Artifact goodId : goodIds) {
      // make sure parent actually exists
      writeArtifact(goodId, emptyMeta, "content");

      Assert.assertEquals(expectedPluginsMapList, artifactStore.getPluginClasses(NamespaceId.DEFAULT, goodId));
      Assert.assertEquals(expectedPluginsMapList,
                          artifactStore.getPluginClasses(NamespaceId.DEFAULT, goodId, "atype"));
      Assert.assertEquals(expectedPluginsMap,
                          artifactStore.getPluginClasses(NamespaceId.DEFAULT, goodId, "atype", "plugin1",
                                                         null, Integer.MAX_VALUE, ArtifactSortOrder.UNORDERED));
    }
  }

  @Category(SlowTests.class)
  @Test
  public void testConcurrentWrite() throws Exception {
    // start up a bunch of threads that will try and write the same artifact at the same time
    // only one of them should be able to write it
    int numThreads = 20;
    final Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "abc", "1.0.0");
    final List<String> successfulWriters = Collections.synchronizedList(Lists.<String>newArrayList());

    // use a barrier so they all try and write at the same time
    final CyclicBarrier barrier = new CyclicBarrier(numThreads);
    final CountDownLatch latch = new CountDownLatch(numThreads);
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < numThreads; i++) {
      final String writer = String.valueOf(i);
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          try {
            barrier.await();
            ArtifactMeta meta = new ArtifactMeta(
              ArtifactClasses.builder()
                .addPlugin(new PluginClass("plugin-type", "plugin" + writer, "", "classname", "cfg",
                  ImmutableMap.<String, PluginPropertyField>of()))
                .build()
            );
            writeArtifact(artifactId, meta, writer);
            successfulWriters.add(writer);
          } catch (InterruptedException | BrokenBarrierException | IOException e) {
            // something went wrong, fail the test
            throw new RuntimeException(e);
          } catch (ArtifactAlreadyExistsException | WriteConflictException e) {
            // these are ok, all but one thread should see this
          } finally {
            latch.countDown();
          }
        }
      });
    }

    // wait for all writers to finish
    latch.await();
    // only one writer should have been able to write
    Assert.assertEquals(1, successfulWriters.size());
    String successfulWriter = successfulWriters.get(0);
    // check that the contents weren't mixed between writers
    ArtifactDetail info = artifactStore.getArtifact(artifactId);
    ArtifactMeta expectedMeta = new ArtifactMeta(
      ArtifactClasses.builder()
        .addPlugin(new PluginClass("plugin-type", "plugin" + successfulWriter, "", "classname", "cfg",
          ImmutableMap.<String, PluginPropertyField>of()))
        .build()
    );
    assertEqual(artifactId, expectedMeta, successfulWriter, info);
  }

  @Test
  public void testUpdateProperties() throws Exception {
    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "abc", "1.2.3");
    Map<String, String> properties = ImmutableMap.of("k1", "v1", "k2", "v2");
    ArtifactMeta meta = new ArtifactMeta(
      ArtifactClasses.builder().build(), ImmutableSet.<ArtifactRange>of(), properties);
    writeArtifact(artifactId, meta, "some contents");

    ArtifactDetail detail = artifactStore.getArtifact(artifactId);
    Assert.assertEquals(properties, detail.getMeta().getProperties());

    // update one key and add a new key
    artifactStore.updateArtifactProperties(artifactId, new Function<Map<String, String>, Map<String, String>>() {
      @Override
      public Map<String, String> apply(Map<String, String> input) {
        return ImmutableMap.of("k3", "v3");
      }
    });
    properties = ImmutableMap.of("k3", "v3");
    detail = artifactStore.getArtifact(artifactId);
    Assert.assertEquals(properties, detail.getMeta().getProperties());
  }

  @Category(SlowTests.class)
  @Test
  public void testConcurrentSnapshotWrite() throws Exception {
    // write parent
    Id.Artifact parentArtifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "parent", "1.0.0");
    ArtifactMeta parentMeta = new ArtifactMeta(ArtifactClasses.builder().build());
    writeArtifact(parentArtifactId, parentMeta, "content");

    final ArtifactRange parentArtifacts = new ArtifactRange(
      NamespaceId.DEFAULT.getNamespace(), "parent", new ArtifactVersion("1.0.0"), new ArtifactVersion("2.0.0"));
    // start up a bunch of threads that will try and write the same artifact at the same time
    // only one of them should be able to write it
    int numThreads = 20;
    final Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.DEFAULT, "abc", "1.0.0-SNAPSHOT");

    // use a barrier so they all try and write at the same time
    final CyclicBarrier barrier = new CyclicBarrier(numThreads);
    final CountDownLatch latch = new CountDownLatch(numThreads);
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < numThreads; i++) {
      final String writer = String.valueOf(i);
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          try {
            barrier.await();
            ArtifactMeta meta = new ArtifactMeta(
              ArtifactClasses.builder()
                .addPlugin(new PluginClass("plugin-type", "plugin" + writer, "", "classname", "cfg",
                  ImmutableMap.<String, PluginPropertyField>of()))
                .build(),
              ImmutableSet.of(parentArtifacts)
            );
            writeArtifact(artifactId, meta, writer);
          } catch (InterruptedException | BrokenBarrierException | ArtifactAlreadyExistsException | IOException e) {
            // something went wrong, fail the test
            throw new RuntimeException(e);
          } catch (WriteConflictException e) {
            // these are ok though unexpected (means couldn't write after a bunch of retries too)
          } finally {
            latch.countDown();
          }
        }
      });
    }

    // wait for all writers to finish
    latch.await();

    // figure out which was the last writer by reading our data. all the writers should have been able to write,
    // and they should have all overwritten each other in a consistent manner
    ArtifactDetail detail = artifactStore.getArtifact(artifactId);
    // figure out the winning writer from the plugin name, which is 'plugin<writer>'
    String pluginName = detail.getMeta().getClasses().getPlugins().iterator().next().getName();
    String winnerWriter = pluginName.substring("plugin".length());

    ArtifactMeta expectedMeta = new ArtifactMeta(
      ArtifactClasses.builder()
        .addPlugin(new PluginClass("plugin-type", "plugin" + winnerWriter, "", "classname", "cfg",
            ImmutableMap.<String, PluginPropertyField>of()))
        .build(),
      ImmutableSet.of(parentArtifacts)
    );
    assertEqual(artifactId, expectedMeta, winnerWriter, detail);

    // check only 1 plugin remains and that its the correct one
    Map<ArtifactDescriptor, Set<PluginClass>> pluginMap =
      artifactStore.getPluginClasses(NamespaceId.DEFAULT, parentArtifactId, "plugin-type");
    Map<ArtifactDescriptor, Set<PluginClass>> expected = Maps.newHashMap();
    expected.put(detail.getDescriptor(), ImmutableSet.<PluginClass>of(
      new PluginClass("plugin-type", "plugin" + winnerWriter, "", "classname", "cfg",
      ImmutableMap.<String, PluginPropertyField>of())));
    Assert.assertEquals(expected, pluginMap);
  }


  private void assertEqual(Id.Artifact expectedId, ArtifactMeta expectedMeta,
                           String expectedContents, ArtifactDetail actual) throws IOException {
    Assert.assertEquals(expectedId.getName(), actual.getDescriptor().getArtifactId().getName());
    Assert.assertEquals(expectedId.getVersion(), actual.getDescriptor().getArtifactId().getVersion());
    Assert.assertEquals(expectedId.getNamespace().equals(Id.Namespace.SYSTEM),
                        actual.getDescriptor().getArtifactId().getScope().equals(ArtifactScope.SYSTEM));
    Assert.assertEquals(expectedMeta, actual.getMeta());
    assertContentsEqual(expectedContents, actual.getDescriptor().getLocation());
  }

  private void assertContentsEqual(String expectedContents, Location location) throws IOException {
    Assert.assertEquals(expectedContents,
                        CharStreams.toString(new InputStreamReader(location.getInputStream(), Charsets.UTF_8)));
  }

  private void writeArtifact(
    Id.Artifact artifactId, ArtifactMeta meta,
    String contents) throws ArtifactAlreadyExistsException, IOException, WriteConflictException {
    artifactStore.write(artifactId, meta, ByteStreams.newInputStreamSupplier(Bytes.toBytes(contents)),
                        new EntityImpersonator(artifactId.toEntityId(),
                                               new DefaultImpersonator(CConfiguration.create(), null)));
  }
}
