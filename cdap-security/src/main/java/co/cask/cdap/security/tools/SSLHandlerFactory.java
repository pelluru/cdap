/*
 * Copyright © 2012-2014 Cask Data, Inc.
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

package co.cask.cdap.security.tools;

import org.jboss.netty.handler.ssl.SslHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * A class that encapsulates SSL Certificate Information
 */
public class SSLHandlerFactory {
  private static final String PROTOCOL = "TLS";
  private static final String ALGORITHM = "SunX509";
  private final SSLContext serverContext;

  public SSLHandlerFactory(File keyStore, String keyStoreType, String keyStorePassword, String certificatePassword) {
    if (keyStore == null) {
      throw new IllegalArgumentException("Key Store Path Not Configured");
    }
    if (keyStorePassword == null) {
      throw new IllegalArgumentException("KeyStore Password Not Configured");
    }

    String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
    if (algorithm == null) {
      algorithm = ALGORITHM;
    }

    try {
      KeyStore ks = KeyStore.getInstance(keyStoreType);
      try (InputStream inputStream = new FileInputStream(keyStore)) {
        ks.load(inputStream, keyStorePassword.toCharArray());
      }
      // Set up key manager factory to use our key store
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
      kmf.init(ks, (certificatePassword != null) ? certificatePassword.toCharArray() : keyStorePassword.toCharArray());

      // Initialize the SSLContext to work with our key managers.
      serverContext = SSLContext.getInstance(PROTOCOL);
      serverContext.init(kmf.getKeyManagers(), null, null);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to initialize the server-side SSLContext", e);
    }
  }

  public SSLHandlerFactory(KeyStore keyStore, String password) {
    try {
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(ALGORITHM);
      serverContext = SSLContext.getInstance(PROTOCOL);
      kmf.init(keyStore, password.toCharArray());
      serverContext.init(kmf.getKeyManagers(), null, null);
    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | UnrecoverableKeyException e) {
      throw new IllegalArgumentException("Failed to initialize the server-side SSLContext", e);
    }
  }

  public SslHandler create() {
    SSLEngine engine = serverContext.createSSLEngine();
    engine.setUseClientMode(false);
    SslHandler handler =  new SslHandler(engine);
    handler.setEnableRenegotiation(false);
    return handler;
  }
}
