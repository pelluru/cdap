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

package co.cask.cdap.internal.app.runtime.artifact.plugin;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.plugin.PluginConfig;

import java.util.concurrent.Callable;

/**
 * plugin doesn't really do anything, just for tests
 */
@Plugin(type = "callable")
@Name("Plugin2")
@Description("Just returns the configured integer")
public class Plugin2 implements Callable<Integer> {
  private P2Config config;

  @Override
  public Integer call() throws Exception {
    return config.v;
  }

  public static class P2Config extends PluginConfig {
    @Description("value to return when called")
    private int v;
  }
}
