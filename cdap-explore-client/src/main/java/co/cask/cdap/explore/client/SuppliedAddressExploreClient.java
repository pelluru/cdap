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

package co.cask.cdap.explore.client;

import com.google.common.base.Supplier;

import java.net.InetSocketAddress;

/**
 * An Explore Client that uses the supplied parameters to talk to a server
 * implementing {@link co.cask.cdap.explore.service.Explore} over HTTP.
 */
public class SuppliedAddressExploreClient extends AbstractExploreClient {

  private final Supplier<String> host;
  private final Supplier<Integer> port;
  private final Supplier<String> authToken;
  private final Supplier<Boolean> sslEnabled;
  private final Supplier<Boolean> verifySSLCert;

  public SuppliedAddressExploreClient(Supplier<String> host, Supplier<Integer> port, Supplier<String> authToken,
                                      Supplier<Boolean> sslEnabled, Supplier<Boolean> verifySSLCert) {
    this.host = host;
    this.port = port;
    this.authToken = authToken;
    this.sslEnabled = sslEnabled;
    this.verifySSLCert = verifySSLCert;
  }

  @Override
  protected InetSocketAddress getExploreServiceAddress() {
    return InetSocketAddress.createUnresolved(host.get(), port.get());
  }

  protected String getAuthToken() {
    return authToken.get();
  }

  @Override
  protected boolean isSSLEnabled() {
    return sslEnabled.get();
  }

  @Override
  protected boolean verifySSLCert() {
    return verifySSLCert.get();
  }
}
