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

package co.cask.cdap.cli.util;

import co.cask.cdap.cli.CLIConnectionConfig;
import co.cask.cdap.client.config.ConnectionConfig;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.proto.id.NamespaceId;
import com.google.inject.Inject;

import java.net.URI;

/**
 */
public class InstanceURIParser {

  public static final InstanceURIParser DEFAULT = new InstanceURIParser(CConfiguration.create());
  public static final String DEFAULT_PROTOCOL = "http";

  private final CConfiguration cConf;

  @Inject
  public InstanceURIParser(CConfiguration cConf) {
    this.cConf = cConf;
  }

  public CLIConnectionConfig parse(String uriString) {
    if (!uriString.contains("://")) {
      uriString = DEFAULT_PROTOCOL + "://" + uriString;
    }

    URI uri = URI.create(uriString);
    NamespaceId namespace =
      (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath())) ?
      NamespaceId.DEFAULT : new NamespaceId(uri.getPath().substring(1));
    String hostname = uri.getHost();
    boolean sslEnabled = "https".equals(uri.getScheme());
    int port = uri.getPort();

    if (port == -1) {
      port = sslEnabled ?
        cConf.getInt(Constants.Router.ROUTER_SSL_PORT) :
        cConf.getInt(Constants.Router.ROUTER_PORT);
    }

    ConnectionConfig config = ConnectionConfig.builder()
      .setHostname(hostname)
      .setPort(port)
      .setSSLEnabled(sslEnabled)
      .build();
    return new CLIConnectionConfig(config, namespace, null);
  }

}
