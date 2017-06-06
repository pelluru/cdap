/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package co.cask.cdap.logging.read;

import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.logging.appender.kafka.StringPartitioner;
import co.cask.cdap.logging.filter.Filter;
import co.cask.cdap.logging.meta.CheckpointManager;
import co.cask.cdap.logging.meta.CheckpointManagerFactory;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads logs in a distributed setup, using kafka for latest logs and files for older logs.
 */
public final class DistributedLogReader implements LogReader {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedLogReader.class);

  private final KafkaLogReader kafkaLogReader;
  private final FileLogReader fileLogReader;
  private final CheckpointManager checkpointManager;
  private final StringPartitioner partitioner;

  /**
   * Creates a DistributedLogReader object.
   */
  @Inject
  DistributedLogReader(CConfiguration cConf,
                       KafkaLogReader kafkaLogReader, FileLogReader fileLogReader,
                       CheckpointManagerFactory checkpointManagerFactory, StringPartitioner partitioner) {
    this.kafkaLogReader = kafkaLogReader;
    this.fileLogReader = fileLogReader;
    this.checkpointManager = checkpointManagerFactory.create(cConf.get(Constants.Logging.KAFKA_TOPIC),
                                                             Constants.Logging.SYSTEM_PIPELINE_CHECKPOINT_PREFIX);
    this.partitioner = partitioner;
  }

  @Override
  public void getLogNext(final LoggingContext loggingContext, final ReadRange readRange, final int maxEvents,
                         final Filter filter, final Callback callback) {
    // If latest logs are not requested, try reading from file.
    if (readRange != ReadRange.LATEST) {
      long checkpointTime = getCheckpointTime(loggingContext);
      // Read from file only if logs are saved for the loggingContext until fromTime
      if (readRange.getFromMillis() < checkpointTime) {
        fileLogReader.getLogNext(loggingContext, readRange, maxEvents, filter, callback);
        // If there are events from fileLogReader, return. Otherwise try in kafkaLogReader.
        if (callback.getCount() != 0) {
          LOG.trace("Got {} log entries from file", callback.getCount());
          return;
        }
      }
    }

    kafkaLogReader.getLogNext(loggingContext, readRange, maxEvents, filter, callback);
    LOG.trace("Got {} log entries from kafka", callback.getCount());

    // No logs in Kafka. This can happen for the latest run of a program, where the logs have been saved and
    // are expired in Kafka, but the checkpoint time is less than run end time - as this is the latest run.
    // In this case, return whatever you can find in saved logs.
    if (callback.getCount() == 0) {
      fileLogReader.getLogNext(loggingContext, readRange, maxEvents, filter, callback);
      LOG.trace("Got {} log entries from file", callback.getCount());
    }
  }

  @Override
  public void getLogPrev(final LoggingContext loggingContext, final ReadRange readRange, final int maxEvents,
                              final Filter filter, final Callback callback) {
    // If latest logs are not requested, try reading from file.
    if (readRange != ReadRange.LATEST) {
      long checkpointTime = getCheckpointTime(loggingContext);
      // Read from file only if logs are saved for the loggingContext until toTime
      if (readRange.getToMillis() < checkpointTime) {
        fileLogReader.getLogPrev(loggingContext, readRange, maxEvents, filter, callback);
        LOG.trace("Got {} log entries from file", callback.getCount());
        return;
      }
    }

    kafkaLogReader.getLogPrev(loggingContext, readRange, maxEvents, filter, callback);
    LOG.trace("Got {} log entries from kafka", callback.getCount());

    // No logs in Kafka. This can happen for the latest run of a program, where the logs have been saved and
    // are expired in Kafka, but the checkpoint time is less than run end time - as this is the latest run.
    // In this case, return whatever you can find in saved logs.
    if (callback.getCount() == 0) {
      fileLogReader.getLogPrev(loggingContext, readRange, maxEvents, filter, callback);
      LOG.trace("Got {} log entries from file", callback.getCount());
    }
  }

  @Override
  public CloseableIterator<LogEvent> getLog(LoggingContext loggingContext, long fromTimeMs, long toTimeMs,
                                            Filter filter) {
    return fileLogReader.getLog(loggingContext, fromTimeMs, toTimeMs, filter);
  }

  private long getCheckpointTime(LoggingContext loggingContext) {
    int partition = partitioner.partition(loggingContext.getLogPartition(), -1);
    try {
      return checkpointManager.getCheckpoint(partition).getMaxEventTime();
    } catch (Exception e) {
      LOG.error("Got exception while reading checkpoint", e);
    }
    return -1;
  }
}
