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

package co.cask.cdap.data.stream.service;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data.stream.StreamFileJanitor;
import com.google.common.util.concurrent.AbstractService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.twill.common.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Performs cleanup of stream files regularly with a single thread executor.
 */
@Singleton
public final class LocalStreamFileJanitorService extends AbstractService implements StreamFileJanitorService {

  private static final Logger LOG = LoggerFactory.getLogger(LocalStreamFileJanitorService.class);

  private final StreamFileJanitor janitor;
  private final long cleanupPeriod;
  private ScheduledExecutorService executor;

  @Inject
  public LocalStreamFileJanitorService(StreamFileJanitor janitor, CConfiguration cConf) {
    this.janitor = janitor;
    this.cleanupPeriod = cConf.getLong(Constants.Stream.FILE_CLEANUP_PERIOD);
  }

  @Override
  protected void doStart() {
    executor = Executors.newSingleThreadScheduledExecutor(Threads.createDaemonThreadFactory("stream-cleanup"));

    // Always run the cleanup when it starts
    executor.submit(new Runnable() {

      @Override
      public void run() {
        if (state() != State.RUNNING) {
          LOG.info("Janitor already stopped");
          return;
        }

        LOG.debug("Execute stream file cleanup.");

        try {
          janitor.cleanAll();
          LOG.debug("Completed stream file cleanup.");
        } catch (Throwable e) {
          LOG.warn("Failed to cleanup stream files.", e);
        } finally {
          // Compute the next cleanup time. It is aligned to work clock based on the period.
          long now = System.currentTimeMillis();
          long delay = (now / cleanupPeriod + 1) * cleanupPeriod - now;

          if (delay <= 0) {
            executor.submit(this);
          } else {
            LOG.debug("Schedule stream file cleanup in {} ms", delay);
            executor.schedule(this, delay, TimeUnit.MILLISECONDS);
          }
        }
      }
    });
    notifyStarted();
  }

  @Override
  protected void doStop() {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        LOG.debug("Stream file janitor stopped");
        notifyStopped();
      }
    });
    executor.shutdown();
  }
}
