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

package co.cask.cdap.common.io;

import java.net.URI;

/**
 * Status of a location.
 */
public class LocationStatus {

  private final URI uri;
  private final long length;
  private final boolean dir;
  private final long lastModified;

  public LocationStatus(URI uri, long length, boolean dir, long lastModified) {
    this.uri = uri;
    this.length = length;
    this.dir = dir;
    this.lastModified = lastModified;
  }

  public URI getUri() {
    return uri;
  }

  public long getLength() {
    return length;
  }

  public boolean isDir() {
    return dir;
  }

  public long getLastModified() {
    return lastModified;
  }
}
