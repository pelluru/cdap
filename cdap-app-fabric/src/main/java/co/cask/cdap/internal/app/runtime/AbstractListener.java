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

package co.cask.cdap.internal.app.runtime;

import co.cask.cdap.app.runtime.ProgramController;

import javax.annotation.Nullable;

/**
 * Base implementation of ProgramController.Listener that does nothing on any its method invocation.
 */
public abstract class AbstractListener implements ProgramController.Listener {

  @Override
  public void init(ProgramController.State currentState, @Nullable Throwable cause) {
    if (currentState == ProgramController.State.COMPLETED) {
      completed();
    } else if (currentState == ProgramController.State.ERROR) {
      error(cause);
    }
  }

  @Override
  public void suspending() {
  }

  @Override
  public void suspended() {
  }

  @Override
  public void resuming() {
  }

  @Override
  public void alive() {
  }

  @Override
  public void stopping() {
  }

  @Override
  public void completed() {
  }

  @Override
  public void killed() {
  }

  @Override
  public void error(Throwable cause) {
  }
}
