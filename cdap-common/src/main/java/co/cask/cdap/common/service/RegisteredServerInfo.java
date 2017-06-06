/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.common.service;

import co.cask.cdap.common.discovery.ServicePayload;

/**
 * Class representing the server info for a registered server.
 */
public class RegisteredServerInfo {
  private ServicePayload payload = new ServicePayload();
  private int port;
  private String address;

  public RegisteredServerInfo(String address, int port) {
    this.address = address;
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public String getAddress() {
    return address;
  }

  public void addPayload(String key, String value) {
    payload.add(key, value);
  }

  public ServicePayload getPayload() {
    return payload;
  }

}
