/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

package co.cask.cdap;

import co.cask.cdap.api.common.RuntimeArguments;
import co.cask.cdap.api.common.Scope;
import co.cask.cdap.internal.app.runtime.BasicArguments;
import co.cask.cdap.proto.ProgramType;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

/**
 * Test RuntimeArguments class.
 */
public class RuntimeArgumentsTest {

  @Test
  public void testMaptoString() {
    Map<String, String> testMap = Maps.newHashMap();
    testMap.put("key1", "val1");
    testMap.put("key2", "val2");
    String [] expectedArray = new String[]{"--key1=val1", "--key2=val2"};
    Arrays.sort(expectedArray);
    String [] receivedArray = RuntimeArguments.toPosixArray(testMap);
    Arrays.sort(receivedArray);
    Assert.assertArrayEquals(receivedArray, expectedArray);
    receivedArray = RuntimeArguments.toPosixArray(new BasicArguments(testMap));
    Arrays.sort(receivedArray);
    Assert.assertArrayEquals(receivedArray, expectedArray);
  }

  @Test
  public void testStringtoMap() {
    String[] argArray = new String[] { "--key1=val1" , "--key2=val2"};
    Map<String, String> expectedMap = Maps.newHashMap();
    expectedMap.put("key1", "val1");
    expectedMap.put("key2", "val2");
    Assert.assertTrue(expectedMap.equals(RuntimeArguments.fromPosixArray(argArray)));
  }

  @Test
  public void testScopedArguments() {
    Map<String, String> runtimeArguments = Maps.newHashMap();

    runtimeArguments.put("debug", "true");
    runtimeArguments.put("mapreduce.*.debug", "false");
    runtimeArguments.put("mapreduce.OneMR.debug", "true");

    runtimeArguments.put("input.path", "global.input.path");
    runtimeArguments.put("mapreduce.OneMR.input.path", "OneMR.input.path");
    runtimeArguments.put("mapreduce.AnotherMR.input.path", "AnotherMR.input.path");
    runtimeArguments.put("spark.*.input.path", "AllSpark.input.path");

    runtimeArguments.put("output.path", "global.output.path");
    runtimeArguments.put("mapreduce.OneMR.output.path", "OneMR.output.path");
    runtimeArguments.put("spark.AnotherSpark.output.path", "AnotherSpark.output.path");

    runtimeArguments.put("mapreduce.*.processing.time", "1HR");

    runtimeArguments.put("dataset.Purchase.cache.seconds", "30");
    runtimeArguments.put("dataset.UserProfile.schema.property", "constant");
    runtimeArguments.put("dataset.unknown.dataset", "false");
    runtimeArguments.put("dataset.*.read.timeout", "60");

    Map<String, String> oneMRArguments = RuntimeArguments.extractScope(ProgramType.MAPREDUCE.getScope(),
                                                                       "OneMR", runtimeArguments);
    Assert.assertTrue(oneMRArguments.size() == 16);
    Assert.assertTrue(oneMRArguments.get("debug").equals("true"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(oneMRArguments.get("input.path").equals("OneMR.input.path"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(oneMRArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(oneMRArguments.get("output.path").equals("OneMR.output.path"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(oneMRArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(oneMRArguments.get("processing.time").equals("1HR"));
    Assert.assertTrue(oneMRArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(oneMRArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(oneMRArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(oneMRArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(oneMRArguments.get("dataset.*.read.timeout").equals("60"));

    Map<String, String> anotherMRArguments = RuntimeArguments.extractScope(ProgramType.MAPREDUCE.getScope(),
                                                                           "AnotherMR", runtimeArguments);
    Assert.assertTrue(anotherMRArguments.size() == 16);
    Assert.assertTrue(anotherMRArguments.get("debug").equals("false"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(anotherMRArguments.get("input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(anotherMRArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(anotherMRArguments.get("output.path").equals("global.output.path"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(anotherMRArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(anotherMRArguments.get("processing.time").equals("1HR"));
    Assert.assertTrue(anotherMRArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(anotherMRArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(anotherMRArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(anotherMRArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(anotherMRArguments.get("dataset.*.read.timeout").equals("60"));

    Map<String, String> oneSparkArguments = RuntimeArguments.extractScope(ProgramType.SPARK.getScope(),
                                                                          "OneSpark", runtimeArguments);
    Assert.assertTrue(oneSparkArguments.size() == 15);
    Assert.assertTrue(oneSparkArguments.get("debug").equals("true"));
    Assert.assertTrue(oneSparkArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(oneSparkArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(oneSparkArguments.get("input.path").equals("AllSpark.input.path"));
    Assert.assertTrue(oneSparkArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(oneSparkArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(oneSparkArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(oneSparkArguments.get("output.path").equals("global.output.path"));
    Assert.assertTrue(oneSparkArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(oneSparkArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(oneSparkArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(oneSparkArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(oneSparkArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(oneSparkArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(oneSparkArguments.get("dataset.*.read.timeout").equals("60"));

    Map<String, String> anotherSparkArguments = RuntimeArguments.extractScope(ProgramType.SPARK.getScope(),
                                                                              "AnotherSpark", runtimeArguments);
    Assert.assertTrue(anotherSparkArguments.size() == 15);
    Assert.assertTrue(anotherSparkArguments.get("debug").equals("true"));
    Assert.assertTrue(anotherSparkArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(anotherSparkArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(anotherSparkArguments.get("input.path").equals("AllSpark.input.path"));
    Assert.assertTrue(anotherSparkArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(anotherSparkArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(anotherSparkArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(anotherSparkArguments.get("output.path").equals("AnotherSpark.output.path"));
    Assert.assertTrue(anotherSparkArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(anotherSparkArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(anotherSparkArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(anotherSparkArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(anotherSparkArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(anotherSparkArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(anotherSparkArguments.get("dataset.*.read.timeout").equals("60"));

    Map<String, String> purchaseArguments = RuntimeArguments.extractScope(Scope.DATASET, "Purchase", oneMRArguments);
    Assert.assertTrue(purchaseArguments.size() == 18);
    Assert.assertTrue(purchaseArguments.get("debug").equals("true"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(purchaseArguments.get("input.path").equals("OneMR.input.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(purchaseArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(purchaseArguments.get("output.path").equals("OneMR.output.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(purchaseArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(purchaseArguments.get("processing.time").equals("1HR"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(purchaseArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(purchaseArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(purchaseArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(purchaseArguments.get("dataset.*.read.timeout").equals("60"));
    Assert.assertTrue(purchaseArguments.get("cache.seconds").equals("30"));
    Assert.assertTrue(purchaseArguments.get("read.timeout").equals("60"));

    Map<String, String> userprofileArguments = RuntimeArguments.extractScope(Scope.DATASET, "UserProfile",
                                                                             oneMRArguments);
    Assert.assertTrue(userprofileArguments.size() == 18);
    Assert.assertTrue(userprofileArguments.get("debug").equals("true"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(userprofileArguments.get("input.path").equals("OneMR.input.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(userprofileArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(userprofileArguments.get("output.path").equals("OneMR.output.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(userprofileArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(userprofileArguments.get("processing.time").equals("1HR"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(userprofileArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(userprofileArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(userprofileArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(userprofileArguments.get("dataset.*.read.timeout").equals("60"));
    Assert.assertTrue(userprofileArguments.get("schema.property").equals("constant"));
    Assert.assertTrue(userprofileArguments.get("read.timeout").equals("60"));

    purchaseArguments = RuntimeArguments.extractScope(Scope.DATASET, "Purchase", oneSparkArguments);
    Assert.assertTrue(purchaseArguments.size() == 17);
    Assert.assertTrue(purchaseArguments.get("debug").equals("true"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(purchaseArguments.get("input.path").equals("AllSpark.input.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(purchaseArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(purchaseArguments.get("output.path").equals("global.output.path"));
    Assert.assertTrue(purchaseArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(purchaseArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(purchaseArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(purchaseArguments.get("cache.seconds").equals("30"));
    Assert.assertTrue(purchaseArguments.get("read.timeout").equals("60"));
    Assert.assertTrue(purchaseArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(purchaseArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(purchaseArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(purchaseArguments.get("dataset.*.read.timeout").equals("60"));

    userprofileArguments = RuntimeArguments.extractScope(Scope.DATASET, "UserProfile", anotherSparkArguments);
    Assert.assertTrue(userprofileArguments.size() == 17);
    Assert.assertTrue(userprofileArguments.get("debug").equals("true"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.*.debug").equals("false"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.debug").equals("true"));

    Assert.assertTrue(userprofileArguments.get("input.path").equals("AllSpark.input.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.input.path").equals("OneMR.input.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.AnotherMR.input.path").equals("AnotherMR.input.path"));
    Assert.assertTrue(userprofileArguments.get("spark.*.input.path").equals("AllSpark.input.path"));

    Assert.assertTrue(userprofileArguments.get("output.path").equals("AnotherSpark.output.path"));
    Assert.assertTrue(userprofileArguments.get("mapreduce.OneMR.output.path").equals("OneMR.output.path"));
    Assert.assertTrue(userprofileArguments.get("spark.AnotherSpark.output.path").equals("AnotherSpark.output.path"));

    Assert.assertTrue(userprofileArguments.get("mapreduce.*.processing.time").equals("1HR"));

    Assert.assertTrue(userprofileArguments.get("schema.property").equals("constant"));
    Assert.assertTrue(userprofileArguments.get("read.timeout").equals("60"));
    Assert.assertTrue(userprofileArguments.get("dataset.Purchase.cache.seconds").equals("30"));
    Assert.assertTrue(userprofileArguments.get("dataset.UserProfile.schema.property").equals("constant"));
    Assert.assertTrue(userprofileArguments.get("dataset.unknown.dataset").equals("false"));
    Assert.assertTrue(userprofileArguments.get("dataset.*.read.timeout").equals("60"));
  }
}
