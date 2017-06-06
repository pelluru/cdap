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

package co.cask.cdap.master.startup;

import co.cask.cdap.common.conf.CConfiguration;

/**
 * Convenience class to group config keys for memory, cores, and instances for each system service.
 */
public class ServiceResourceKeys {
  private final String serviceName;
  private final String memoryKey;
  private final String vcoresKey;
  private final String instancesKey;
  private final String maxInstancesKey;
  protected final CConfiguration cConf;

  public ServiceResourceKeys(CConfiguration cConf, String serviceName, String memoryKey, String vcoresKey,
                             String instancesKey, String maxInstancesKey) {
    this.cConf = cConf;
    this.serviceName = serviceName;
    this.memoryKey = memoryKey;
    this.vcoresKey = vcoresKey;
    this.instancesKey = instancesKey;
    this.maxInstancesKey = maxInstancesKey;
  }

  public String getServiceName() {
    return serviceName;
  }

  public String getMemoryKey() {
    return memoryKey;
  }

  public int getMemory() {
    return cConf.getInt(memoryKey);
  }

  public String getVcoresKey() {
    return vcoresKey;
  }

  public int getVcores() {
    return cConf.getInt(vcoresKey);
  }

  public String getInstancesKey() {
    return instancesKey;
  }

  public int getInstances() {
    return cConf.getInt(instancesKey);
  }

  public String getMaxInstancesKey() {
    return maxInstancesKey;
  }

  public int getMaxInstances() {
    return cConf.getInt(maxInstancesKey);
  }
}
