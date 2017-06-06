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

package co.cask.cdap.api.data.format;

import co.cask.cdap.api.annotation.Beta;

/**
 * Exception to indicate data is of an unexpected format.
 */
@Beta
public class UnexpectedFormatException extends RuntimeException {
  public UnexpectedFormatException(String message) {
    super(message);
  }

  public UnexpectedFormatException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnexpectedFormatException(Throwable cause) {
    super(cause);
  }
}
