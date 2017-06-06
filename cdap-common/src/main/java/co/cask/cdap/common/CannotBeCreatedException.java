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

package co.cask.cdap.common;

import co.cask.cdap.proto.id.EntityId;

/**
 * Thrown when an element cannot be created.
 */
public class CannotBeCreatedException extends Exception {

  private final EntityId entityId;
  private final String reason;

  public CannotBeCreatedException(EntityId entityId, Throwable cause) {
    super(String.format("'%s' cannot be created. Reason: %s", entityId, cause.getMessage()), cause);
    this.entityId = entityId;
    this.reason = cause.getMessage();
  }

  public EntityId getEntityId() {
    return entityId;
  }

  public String getReason() {
    return reason;
  }
}
