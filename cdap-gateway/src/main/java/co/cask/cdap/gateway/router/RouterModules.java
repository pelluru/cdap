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

package co.cask.cdap.gateway.router;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.runtime.RuntimeModule;
import co.cask.cdap.common.utils.Networks;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.name.Named;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Router guice modules.
 */
public class RouterModules extends RuntimeModule {
  @Override
  public Module getInMemoryModules() {
    return getCommonModules();
  }

  @Override
  public Module getStandaloneModules() {
    return getCommonModules();
  }

  @Override
  public Module getDistributedModules() {
    return getCommonModules();
  }

  private Module getCommonModules() {
    return new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides
      @Named(Constants.Router.ADDRESS)
      @SuppressWarnings("unused")
      public InetAddress providesHostname(CConfiguration cConf) {
        return Networks.resolve(cConf.get(Constants.Router.ADDRESS),
                                new InetSocketAddress("localhost", 0).getAddress());
      }
    };
  }
}
