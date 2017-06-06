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

package co.cask.cdap.proto.codec;

import co.cask.cdap.api.schedule.Schedule;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.api.schedule.Schedules;
import co.cask.cdap.api.workflow.ScheduleProgramInfo;
import co.cask.cdap.internal.schedule.StreamSizeSchedule;
import co.cask.cdap.internal.schedule.TimeSchedule;
import co.cask.cdap.proto.ScheduleType;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.Map;

/**
 *
 */
public class ScheduleSpecificationCodec extends AbstractSpecificationCodec<ScheduleSpecification> {

  @SuppressWarnings("deprecation")
  @Override
  public JsonElement serialize(ScheduleSpecification src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    ScheduleType scheduleType = ScheduleType.fromSchedule(src.getSchedule());
    if (scheduleType.equals(ScheduleType.TIME)) {
      jsonObj.add("scheduleType", context.serialize(ScheduleType.TIME, ScheduleType.class));
      jsonObj.add("schedule", context.serialize(src.getSchedule(), TimeSchedule.class));
    } else if (scheduleType.equals(ScheduleType.STREAM)) {
      jsonObj.add("scheduleType", context.serialize(ScheduleType.STREAM, ScheduleType.class));
      jsonObj.add("schedule", context.serialize(src.getSchedule(), StreamSizeSchedule.class));
    }

    jsonObj.add("program", context.serialize(src.getProgram(), ScheduleProgramInfo.class));
    jsonObj.add("properties", serializeMap(src.getProperties(), context, String.class));
    return jsonObj;
  }

  @Override
  public ScheduleSpecification deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    JsonElement scheduleTypeJson = jsonObj.get("scheduleType");
    ScheduleType scheduleType;
    if (scheduleTypeJson == null) {
      // For backwards compatibility with spec persisted with older versions than 2.8, we need these lines
      scheduleType = null;
    } else {
      scheduleType = context.deserialize(jsonObj.get("scheduleType"), ScheduleType.class);
    }

    Schedule schedule = null;
    if (scheduleType == null) {
      JsonObject scheduleObj = jsonObj.get("schedule").getAsJsonObject();
      String name = context.deserialize(scheduleObj.get("name"), String.class);
      String description = context.deserialize(scheduleObj.get("description"), String.class);
      String cronEntry = context.deserialize(scheduleObj.get("cronEntry"), String.class);
      schedule = Schedules.builder(name).setDescription(description).createTimeSchedule(cronEntry);
    } else {
      switch (scheduleType) {
        case TIME:
          schedule = context.deserialize(jsonObj.get("schedule"), TimeSchedule.class);
          break;
        case STREAM:
          schedule = context.deserialize(jsonObj.get("schedule"), StreamSizeSchedule.class);
          break;
      }
    }

    ScheduleProgramInfo program = context.deserialize(jsonObj.get("program"), ScheduleProgramInfo.class);
    Map<String, String> properties = deserializeMap(jsonObj.get("properties"), context, String.class);
    return new ScheduleSpecification(schedule, program, properties);
  }
}
