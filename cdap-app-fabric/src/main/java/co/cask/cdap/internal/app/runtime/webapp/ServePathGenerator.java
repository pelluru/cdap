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

package co.cask.cdap.internal.app.runtime.webapp;

import co.cask.cdap.common.conf.Constants;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import java.net.URI;

/**
 * Determines the path to serve based on the Host header.
 */
public class ServePathGenerator {
  public static final String SRC_PATH = "/src/";
  public static final String DEFAULT_DIR_NAME = "default";
  private static final String GATEWAY_PATH_V3 = Constants.Gateway.API_VERSION_3.substring(1) + "/";

  private static final String DEFAULT_PORT_STR = ":80";

  private final String baseDir;
  private final Predicate<String> fileExists;

  public ServePathGenerator(String baseDir, Predicate<String> fileExists) {
    this.baseDir = baseDir.replaceAll("/+$", "");
    this.fileExists = fileExists;
  }

  public String getServePath(String hostHeader, String uriString) {
    URI uri = URI.create(uriString);
    String path = uri.getPath();
    String query = uri.getQuery();

    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    // If exact match present, return it
    String servePath = findPath(hostHeader, path, query);
    if (servePath != null) {
      return constructURI(servePath);
    }

    boolean isDefaultPort = hostHeader.endsWith(DEFAULT_PORT_STR);
    boolean hasNoPort = hostHeader.indexOf(':') == -1;

    // Strip DEFAULT_PORT_STR and try again
    if (isDefaultPort) {
      servePath = findPath(hostHeader.substring(0, hostHeader.length() - DEFAULT_PORT_STR.length()), path, query);
      if (servePath != null) {
        return constructURI(servePath);
      }
    }

    // Add DEFAULT_PORT_STR and try
    if (hasNoPort) {
      servePath = findPath(hostHeader + DEFAULT_PORT_STR, path, query);
      if (servePath != null) {
        return constructURI(servePath);
      }
    }

    // Else if "default" is present, that is the serve dir
    servePath = findPath(DEFAULT_DIR_NAME, path, query);
    if (servePath != null) {
      return constructURI(servePath);
    }

    return constructURI(path);
  }

  private String constructURI(String servePath) {
    return servePath.startsWith("/") ? servePath : "/" + servePath;
  }

  private String findPath(String hostHeader, String path, String query) {
    // First try firstPathPart/src/restPath
    Iterable<String> pathParts = Splitter.on('/').limit(2).split(path);
    String servePath;
    if (Iterables.size(pathParts) > 1) {
      String part1 = Iterables.get(pathParts, 1);
      if (part1.startsWith(GATEWAY_PATH_V3) || part1.equals("status")) {
        return constructPath(part1, query);
      }

      servePath = String.format("%s/%s/%s%s%s", baseDir, hostHeader,
                                       Iterables.get(pathParts, 0), SRC_PATH, Iterables.get(pathParts, 1));
      if (fileExists.apply(servePath)) {
        return servePath;
      }

    } else if (Iterables.size(pathParts) == 1) {
      servePath = String.format("%s/%s/%s%s%s", baseDir, hostHeader,
                                Iterables.get(pathParts, 0), SRC_PATH, "index.html");
      if (fileExists.apply(servePath)) {
        return servePath;
      }
    }

    // Next try src/path
    if (path.startsWith(GATEWAY_PATH_V3) || path.equals("status")) {
      return constructPath(path, query);
    }

    path = path.isEmpty() ? "index.html" : path;
    servePath = String.format("%s/%s%s%s", baseDir, hostHeader, SRC_PATH, path);
    if (fileExists.apply(servePath)) {
      return servePath;
    }

    return null;
  }

  private String constructPath(String path, String query) {
    return query == null ? path : String.format("%s?%s", path, query);
  }
}
