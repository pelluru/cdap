/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.internal.app.runtime.schedule.store;

import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.dataset.DatasetManagementException;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.schedule.Schedule;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.internal.app.runtime.schedule.ProgramSchedule;
import co.cask.cdap.internal.app.runtime.schedule.constraint.ConcurrencyConstraint;
import co.cask.cdap.internal.app.runtime.schedule.queue.JobQueueDataset;
import co.cask.cdap.internal.app.runtime.schedule.trigger.StreamSizeTrigger;
import co.cask.cdap.internal.app.runtime.schedule.trigger.TimeTrigger;
import co.cask.cdap.internal.schedule.ScheduleCreationSpec;
import co.cask.cdap.internal.schedule.StreamSizeSchedule;
import co.cask.cdap.internal.schedule.TimeSchedule;
import co.cask.cdap.internal.schedule.constraint.Constraint;
import co.cask.cdap.internal.schedule.trigger.Trigger;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.ScheduleDetail;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import joptsimple.internal.Strings;
import org.quartz.CronExpression;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Common utility methods for scheduling.
 */
public class Schedulers {
  public static final String STORE_TYPE_NAME = ProgramScheduleStoreDataset.class.getSimpleName();
  public static final DatasetId STORE_DATASET_ID = NamespaceId.SYSTEM.dataset("schedule.store");
  public static final DatasetId JOB_QUEUE_DATASET_ID = NamespaceId.SYSTEM.dataset("job.queue");

  public static final Type SCHEDULE_DETAILS_TYPE = new TypeToken<List<ScheduleDetail>>() { }.getType();
  public static final Type SCHEDULE_SPECS_TYPE = new TypeToken<List<ScheduleSpecification>>() { }.getType();

  public static final long JOB_QUEUE_TIMEOUT_MILLIS = TimeUnit.DAYS.toMillis(1);

  public static final int SUBSCRIBER_TX_TIMEOUT_SECONDS = 30;
  public static final long SUBSCRIBER_TX_TIMEOUT_MILLIS = 1000 * (long) SUBSCRIBER_TX_TIMEOUT_SECONDS;

  public static String triggerKeyForPartition(DatasetId datasetId) {
    return "partition:" + datasetId.getNamespace() + '.' + datasetId.getDataset();
  }

  public static JobQueueDataset getJobQueue(DatasetContext context, DatasetFramework dsFramework) {
    try {
      return DatasetsUtil.getOrCreateDataset(context, dsFramework, JOB_QUEUE_DATASET_ID,
                                             JobQueueDataset.class.getName(), DatasetProperties.EMPTY);
    } catch (DatasetManagementException | IOException e) {
      throw Throwables.propagate(e);
    }
  }

  public static ProgramScheduleStoreDataset getScheduleStore(DatasetContext context, DatasetFramework dsFramework) {
    try {
      return DatasetsUtil
        .getOrCreateDataset(context, dsFramework, STORE_DATASET_ID, STORE_TYPE_NAME, DatasetProperties.EMPTY);
    } catch (DatasetManagementException | IOException e) {
      throw Throwables.propagate(e);
    }
  }

  public static ScheduleCreationSpec toScheduleCreationSpec(NamespaceId deployNamespace, Schedule schedule,
                                                            String programName, Map<String, String> properties) {
    Trigger trigger;
    if (schedule instanceof TimeSchedule) {
      trigger = new TimeTrigger(((TimeSchedule) schedule).getCronEntry());
    } else {
      StreamSizeSchedule streamSizeSchedule = ((StreamSizeSchedule) schedule);
      trigger = new StreamSizeTrigger(deployNamespace.stream(streamSizeSchedule.getStreamName()),
                                      streamSizeSchedule.getDataTriggerMB());
    }
    Integer maxConcurrentRuns = schedule.getRunConstraints().getMaxConcurrentRuns();
    List<Constraint> constraints = maxConcurrentRuns == null ? ImmutableList.<Constraint>of() :
      ImmutableList.<Constraint>of(new ConcurrencyConstraint(maxConcurrentRuns));
    return new ScheduleCreationSpec(schedule.getName(), schedule.getDescription(), programName,
                                    properties, trigger, constraints, Schedulers.JOB_QUEUE_TIMEOUT_MILLIS);
  }

  public static ProgramSchedule toProgramSchedule(ApplicationId appId, ScheduleSpecification spec) {
    Schedule schedule = spec.getSchedule();
    ProgramType programType = ProgramType.valueOfSchedulableType(spec.getProgram().getProgramType());
    ProgramId programId = appId.program(programType, spec.getProgram().getProgramName());
    Trigger trigger;
    if (schedule instanceof TimeSchedule) {
      TimeSchedule timeSchedule = (TimeSchedule) schedule;
      trigger = new TimeTrigger(timeSchedule.getCronEntry());
    } else {
      StreamSizeSchedule streamSchedule = (StreamSizeSchedule) schedule;
      StreamId streamId = programId.getNamespaceId().stream(streamSchedule.getStreamName());
      trigger = new StreamSizeTrigger(streamId, streamSchedule.getDataTriggerMB());
    }
    Integer maxConcurrentRuns = schedule.getRunConstraints().getMaxConcurrentRuns();
    List<Constraint> constraints = maxConcurrentRuns == null ? ImmutableList.<Constraint>of() :
      ImmutableList.<Constraint>of(new ConcurrencyConstraint(maxConcurrentRuns));
    return new ProgramSchedule(schedule.getName(), schedule.getDescription(),
                               programId, spec.getProperties(), trigger, constraints);
  }

  /**
   * Convert a list of program schedules into a list of schedule details.
   */
  public static List<ScheduleDetail> toScheduleDetails(List<ProgramSchedule> schedules) {
    return Lists.transform(schedules, new Function<ProgramSchedule, ScheduleDetail>() {
      @Nullable
      @Override
      public ScheduleDetail apply(@Nullable ProgramSchedule input) {
        return input == null ? null : input.toScheduleDetail();
      }
    });
  }

  public static StreamSizeSchedule toStreamSizeSchedule(ProgramSchedule schedule) {
    StreamSizeTrigger trigger = (StreamSizeTrigger) schedule.getTrigger();
    return new StreamSizeSchedule(schedule.getName(), schedule.getDescription(),
                                  trigger.getStreamId().getStream(), trigger.getTriggerMB());
  }

  public static void validateCronExpression(String cronExpression) {
    String quartzCron = getQuartzCronExpression(cronExpression);
    try {
      CronExpression.validateExpression(quartzCron);
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  // Helper function to adapt cron entry to a cronExpression that is usable by Quartz with the following rules:
  // 1. Quartz doesn't support specifying both day-of-the-month and day-of-the-week,
  //    one of them must be '?' if the other is specified with anything other than '?'
  // 2. Quartz resolution is in seconds which cron entry doesn't support.
  public static String getQuartzCronExpression(String cronEntry) {
    // Checks if the cronEntry is quartz cron Expression or unix like cronEntry format.
    // CronExpression will directly be used for tests.
    String parts [] = cronEntry.split(" ");
    Preconditions.checkArgument(parts.length >= 5 , "Invalid cron entry format");
    if (parts.length == 5) {
      // Convert cron entry format to Quartz format by replacing wild-card character "*"
      // if day-of-the-month is not "?" and day-of-the-week is wild-card, replace day-of-the-week with "?"
      if (!parts[2].equals("?") && parts[4].equals("*")) {
        parts[4] = "?";
      }
      // if day-of-the-week is not "?" and day-of-the-month is wild-card, replace day-of-the-month with "?"
      if (!parts[4].equals("?") && parts[2].equals("*")) {
        parts[2] = "?";
      }
      List<String> partsList = new ArrayList<>(Arrays.asList(parts));
      // add "0" to seconds in Quartz format without changing the meaning of the original cron expression
      partsList.add(0, "0");
      return Strings.join(partsList, " ");
    } else {
      //Use the given cronExpression
      return cronEntry;
    }
  }
}
