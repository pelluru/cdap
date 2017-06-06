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

package co.cask.cdap.proto.metadata.lineage;

import co.cask.cdap.proto.id.NamespacedEntityId;

import java.util.Objects;

/**
 * Class to serialize program in {@link LineageRecord}.
 */
public class ProgramRecord {
  private final NamespacedEntityId entityId;

  public ProgramRecord(NamespacedEntityId entityId) {
    this.entityId = entityId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProgramRecord)) {
      return false;
    }
    ProgramRecord that = (ProgramRecord) o;
    return Objects.equals(entityId, that.entityId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityId);
  }

  @Override
  public String toString() {
    return "ProgramRecord{" +
      "entityId=" + entityId +
      '}';
  }
}
