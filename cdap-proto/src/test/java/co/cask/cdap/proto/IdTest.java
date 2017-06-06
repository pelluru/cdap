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

package co.cask.cdap.proto;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class IdTest {

  private static final Gson GSON = new Gson();

  @Test
  public void testFlow() {
    Id.Flow flow = Id.Flow.from("ns1", "app1", "flow1");
    Id.Flow sameFlow = Id.Flow.from("ns1", "app1", "flow1");
    Assert.assertEquals(sameFlow, flow);
    Assert.assertEquals(sameFlow.toString(), flow.toString());

    Id.Program program = Id.Program.from("ns1", "app1", ProgramType.FLOW, "flow1");
    Assert.assertEquals(flow, program);
    Assert.assertEquals(flow.toString(), program.toString());

    Id.Program notSame = Id.Program.from("ns1", "app1", ProgramType.MAPREDUCE, "flow1");
    Assert.assertNotEquals(flow, notSame);
    Assert.assertNotEquals(flow.toString(), notSame.toString());
  }

  @Test
  public void testJsonHashCode() {
    Id.Flow flow = Id.Flow.from("ns1", "app1", "flow1");
    int hashCode = flow.hashCode();
    String toString = flow.toString();

    String json = GSON.toJson(flow);
    Assert.assertFalse(json.contains(Integer.toString(hashCode)));
    Assert.assertFalse(json.contains(toString));

    Id.Flow sameFlow = GSON.fromJson(json, Id.Flow.class);
    Assert.assertEquals(hashCode, sameFlow.hashCode());
    Assert.assertEquals(toString, sameFlow.toString());
    Assert.assertEquals(flow, sameFlow);
  }

  @Test
  public void testArtifactIdTest() {
    Assert.assertTrue(Id.Artifact.isValidName("cdap-artifact-id_2.10-1.0.0"));
  }

}
