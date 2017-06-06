/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

package co.cask.cdap.gateway.handlers.log;

import co.cask.cdap.logging.read.LogOffset;
import com.google.common.base.Objects;

/**
* Test Log object.
*/
class LogLine extends OffsetLine {
  private final String log;

  LogLine(LogOffset offset, String log) {
    super(offset);
    this.log = log;
  }

  public String getLog() {
    return log;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("offset", getOffset())
      .add("log", log)
      .toString();
  }
}
