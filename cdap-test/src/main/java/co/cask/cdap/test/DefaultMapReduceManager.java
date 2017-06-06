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

package co.cask.cdap.test;

import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramRunStatus;

/**
 * A default implementation of {@link MapReduceManager}.
 */
public class DefaultMapReduceManager extends AbstractProgramManager<MapReduceManager> implements MapReduceManager {

  public DefaultMapReduceManager(Id.Program programId, ApplicationManager applicationManager) {
    super(programId, applicationManager);
  }

  @Override
  public boolean isRunning() {
    // workaround until CDAP-7479 is fixed
    return super.isRunning() || !getHistory(ProgramRunStatus.RUNNING).isEmpty();
  }
}
