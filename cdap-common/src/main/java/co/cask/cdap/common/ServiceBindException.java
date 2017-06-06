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

package co.cask.cdap.common;

import java.io.IOException;

/**
 * Thrown when a system service fails to bind to a socket address.
 */
public class ServiceBindException extends IOException {

  private final String service;
  private final String host;
  private final int port;

  public ServiceBindException(String service, String host, int port, Exception cause) {
    super(String.format("%s failed to bind to %s:%d", service, host, port), cause);
    this.service = service;
    this.host = host;
    this.port = port;
  }

  public String getService() {
    return service;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }
}
