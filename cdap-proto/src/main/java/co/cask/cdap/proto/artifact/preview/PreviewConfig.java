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

package co.cask.cdap.proto.artifact.preview;

import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.artifact.AppRequest;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Preview configuration in the {@link AppRequest}.
 */
public class PreviewConfig {
  private final String programName;
  private final ProgramType programType;
  private final Map<String, String> runtimeArgs;
  // The timeout unit is minutes.
  private final Integer timeout;

  public PreviewConfig(String programName, ProgramType programType, @Nullable Map<String, String> runtimeArgs,
                       @Nullable Integer timeout) {
    this.programName = programName;
    this.programType = programType;
    this.runtimeArgs = runtimeArgs == null ? new HashMap<String, String>() : new HashMap<>(runtimeArgs);
    this.timeout = timeout;
  }

  /**
   * @return the program name.
   */
  public String getProgramName() {
    return programName;
  }

  /**
   * @return the {@link ProgramType} of the preview.
   */
  public ProgramType getProgramType() {
    return programType;
  }

  /**
   * @return the {@link Map} of runtime arguments of the preview.
   */
  public Map<String, String> getRuntimeArgs() {
    return runtimeArgs;
  }

  /**
   * Get the timeout for the preview run, the time unit is minutes, null if not provided.
   *
   * @return the timeout for the preview.
   */
  @Nullable
  public Integer getTimeout() {
    return timeout;
  }
}
