/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.app.runtime;

import co.cask.cdap.app.program.ProgramDescriptor;
import co.cask.cdap.proto.ProgramLiveInfo;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ProgramId;
import com.google.common.util.concurrent.Service;
import org.apache.twill.api.RunId;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Service for interacting with the runtime system.
 */
public interface ProgramRuntimeService extends Service {

  /**
   * Represents information of a running program.
   */
  interface RuntimeInfo {
    ProgramController getController();

    ProgramType getType();

    ProgramId getProgramId();

    @Nullable
    RunId getTwillRunId();
  }

  /**
   * Starts the given program and return a {@link RuntimeInfo} about the running program.
   *
   * @param programDescriptor describing the program to run
   * @param options {@link ProgramOptions} that are needed by the program.
   * @return A {@link ProgramController} for the running program.
   */
  RuntimeInfo run(ProgramDescriptor programDescriptor, ProgramOptions options);

  /**
   * Find the {@link RuntimeInfo} for a running program with the given {@link RunId}.
   *
   * @param programId The id of the program.
   * @param runId     The program {@link RunId}.
   * @return A {@link RuntimeInfo} for the running program or {@code null} if no such program is found.
   */
  RuntimeInfo lookup(ProgramId programId, RunId runId);

  /**
   * Get {@link RuntimeInfo} for all running programs of the given type.
   *
   * @param type Type of running programs to list.
   * @return An immutable map from {@link RunId} to {@link ProgramController}.
   */
  Map<RunId, RuntimeInfo> list(ProgramType type);

  /**
   * Get {@link RuntimeInfo} for a specified program.
   * @param program The program for which the {@link RuntimeInfo} needs to be determined
   * @return An immutable map from {@link RunId} to {@link ProgramController}
   */
  Map<RunId, RuntimeInfo> list(ProgramId program);

  /**
   * Get runtime information about a running program. The content of this information is different
   * for each runtime environment. For example, in a distributed environment, this would contain the
   * YARN application id and the container information for each runnable. For in-memory, it may be empty.
   */
  ProgramLiveInfo getLiveInfo(ProgramId programId);

  /**
   * Get information about running programs.
   * Protected only to support v2 APIs
   *
   * @param types Types of program to check
   * returns List of info about running programs.
   */
  List<RuntimeInfo> listAll(ProgramType... types);
}
