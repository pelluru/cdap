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

package co.cask.cdap.test.app;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.worker.AbstractWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * App with a no-op worker.
 */
public class NoOpWorkerApp extends AbstractApplication {

  @Override
  public void configure() {
    addWorker(new NoOpWorker());
  }

  public static class NoOpWorker extends AbstractWorker {
    private static final Logger LOG = LoggerFactory.getLogger(NoOpWorker.class);

    @Override
    public void run() {
      LOG.info("NoOp {}", getContext().getInstanceId());
    }

    @Override
    protected void configure() {
      setInstances(5);
    }
  }
}
