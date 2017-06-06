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

package co.cask.cdap.common.lang;

import javax.annotation.Nullable;

/**
 * Represents class that performs operation by delegating to another object.
 *
 * @param <T> Type of the object that delegates to.
 */
public interface Delegator<T> {

  /**
   * Returns the delegating object or {@code null} if there is no delegating object.
   */
  @Nullable
  T getDelegate();
}
