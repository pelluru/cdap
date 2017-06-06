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
package co.cask.cdap.internal.app.runtime.workflow;

import co.cask.cdap.app.program.Program;
import co.cask.cdap.internal.app.runtime.AbstractProgramController;
import com.google.common.util.concurrent.Service;
import org.apache.twill.api.RunId;
import org.apache.twill.api.ServiceAnnouncer;
import org.apache.twill.common.Cancellable;
import org.apache.twill.common.Threads;
import org.apache.twill.internal.ServiceListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 *
 */
final class WorkflowProgramController extends AbstractProgramController {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowProgramController.class);

  private final WorkflowDriver driver;
  private final String serviceName;
  private final ServiceAnnouncer serviceAnnouncer;
  private Cancellable cancelAnnounce;

  WorkflowProgramController(Program program, WorkflowDriver driver, ServiceAnnouncer serviceAnnouncer, RunId runId) {
    super(program.getId(), runId);
    this.driver = driver;
    this.serviceName = getServiceName(program, runId);
    this.serviceAnnouncer = serviceAnnouncer;
    startListen(driver);
  }

  @Override
  protected void doSuspend() throws Exception {
    driver.suspend();
  }

  @Override
  protected void doResume() throws Exception {
    driver.resume();
  }

  @Override
  protected void doStop() throws Exception {
    driver.stopAndWait();
  }

  @Override
  protected void doCommand(String name, Object value) throws Exception {
    LOG.info("Command ignored {}, {}", name, value);
  }

  private void startListen(Service service) {
    // Forward state changes from the given service to this controller.
    service.addListener(new ServiceListenerAdapter() {
      @Override
      public void running() {
        InetSocketAddress endpoint = driver.getServiceEndpoint();
        cancelAnnounce = serviceAnnouncer.announce(serviceName, endpoint.getPort());
        LOG.debug("Workflow service {} announced at {}", serviceName, endpoint);
        started();
      }

      @Override
      public void terminated(Service.State from) {
        LOG.debug("Workflow service terminated from {}. Un-registering service {}.", from, serviceName);
        cancelAnnounce.cancel();
        LOG.debug("Service {} unregistered.", serviceName);
        if (getState() != State.STOPPING) {
          // service completed itself.
          complete();
        } else {
          // service was terminated
          stop();
        }
      }

      @Override
      public void failed(Service.State from, Throwable failure) {
        LOG.error("Workflow service '{}' failed.", serviceName, failure);
        if (cancelAnnounce != null) {
          // if there is an exception before workflow enters into the RUNNING state, cancelAnnounce will be null
          // since it is initialized in the running method
          cancelAnnounce.cancel();
        }
        LOG.info("Service {} unregistered.", serviceName);
        error(failure);
      }
    }, Threads.SAME_THREAD_EXECUTOR);
  }

  private String getServiceName(Program program, RunId runId) {
    return String.format("workflow.%s.%s.%s.%s",
                         program.getNamespaceId(), program.getApplicationId(), program.getName(), runId.getId());
  }
}
