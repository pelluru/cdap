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

package co.cask.cdap.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 *
 */
public class ConfigurationJsonToolTest {

  @Test
  public void testLogSuppress() throws UnsupportedEncodingException {
    PrintStream stdout = System.out;

    // Run the config tool. It should only prints the config json to System.out
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    System.setOut(new PrintStream(os, true, "UTF-8"));
    try {
      ConfigurationJsonTool.main(new String[]{"--cdap"});
    } finally {
      System.setOut(stdout);
    }

    // The JSON parsing should succeed.
    new Gson().fromJson(os.toString("UTF-8"), JsonObject.class);
  }
}
