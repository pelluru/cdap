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

package co.cask.cdap.proto;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Describes the status of a program, as returned by the batch status endpoint POST /namespaces/{namespace}/status.
 */
public class BatchProgramStatus extends BatchProgramResult {
  private final String status;

  public BatchProgramStatus(BatchProgram program, int statusCode, @Nullable String error, @Nullable String status) {
    super(program, statusCode, error);
    this.status = status;
  }

  /**
   * @return the status of the program. Null if there was an error.
   */
  @Nullable
  public String getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    BatchProgramStatus that = (BatchProgramStatus) o;

    return Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), status);
  }
}
