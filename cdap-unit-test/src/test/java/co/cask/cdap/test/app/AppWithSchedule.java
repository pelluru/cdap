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

package co.cask.cdap.test.app;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.app.ProgramType;
import co.cask.cdap.api.customaction.AbstractCustomAction;
import co.cask.cdap.api.data.schema.UnsupportedTypeException;
import co.cask.cdap.api.dataset.lib.ObjectStores;
import co.cask.cdap.api.schedule.Schedules;
import co.cask.cdap.api.workflow.AbstractWorkflow;
import co.cask.cdap.api.workflow.Value;
import co.cask.cdap.api.workflow.WorkflowToken;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Application with workflow scheduling.
 */
public class AppWithSchedule extends AbstractApplication {

  public static final String SCHEDULE_NAME = "SampleSchedule";

  @Override
  public void configure() {
    try {
      setName("AppWithSchedule");
      setDescription("Sample application");
      ObjectStores.createObjectStore(getConfigurer(), "input", String.class);
      ObjectStores.createObjectStore(getConfigurer(), "output", String.class);
      addWorkflow(new SampleWorkflow());
      schedule(buildSchedule(SCHEDULE_NAME, ProgramType.WORKFLOW, SampleWorkflow.class.getSimpleName())
                 .triggerByTime("0/1 * * * * ?"));
    } catch (UnsupportedTypeException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Sample workflow. Schedules a dummy MR job.
   */
  public static class SampleWorkflow extends AbstractWorkflow {

    @Override
    public void configure() {
        setDescription("SampleWorkflow description");
        addAction(new DummyAction());
    }
  }

  /**
   * DummyAction
   */
  public static class DummyAction extends AbstractCustomAction {
    private static final Logger LOG = LoggerFactory.getLogger(DummyAction.class);

    @Override
    public void initialize() throws Exception {
      WorkflowToken token = getContext().getWorkflowToken();
      token.put("running", Value.of(true));
      token.put("finished", Value.of(false));
    }

    @Override
    public void run() {
      LOG.info("Ran dummy action");
      try {
        TimeUnit.MILLISECONDS.sleep(500);
      } catch (InterruptedException e) {
        LOG.info("Interrupted");
      }
    }

    @Override
    public void destroy() {
      WorkflowToken token = getContext().getWorkflowToken();
      token.put("running", Value.of(false));
      token.put("finished", Value.of(true));
    }
  }
}
