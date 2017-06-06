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

package co.cask.cdap.explore.service;

/**
 * Exception thrown when {@link co.cask.cdap.proto.QueryHandle} is not found.
 */
public class HandleNotFoundException extends Exception {
  private final boolean isInactive;

  public HandleNotFoundException(String s) {
    this(s, false);
  }

  public HandleNotFoundException(String s, boolean isInactive) {
    super(s);
    this.isInactive = isInactive;
  }

  public boolean isInactive() {
    return isInactive;
  }
}
