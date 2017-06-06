/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

package co.cask.cdap.etl.common;

import co.cask.cdap.api.data.schema.Schema;

/**
 * Constants used in ETL Applications.
 */
public final class Constants {
  public static final String ID_SEPARATOR = ":";
  public static final String PIPELINEID = "pipeline";
  public static final String PIPELINE_SPEC_KEY = "pipeline.spec";
  public static final String STAGE_LOGGING_ENABLED = "stage.logging.enabled";
  public static final String CONNECTOR_TYPE = "connector";
  public static final String EVENT_TYPE_TAG = "MDC:eventType";
  public static final String PIPELINE_LIFECYCLE_TAG_VALUE = "lifecycle";
  public static final String SPARK_PROGRAM_PLUGIN_TYPE = "sparkprogram";
  public static final Schema ERROR_SCHEMA = Schema.recordOf(
    "error",
    Schema.Field.of(ErrorDataset.ERRCODE, Schema.of(Schema.Type.INT)),
    Schema.Field.of(ErrorDataset.ERRMSG, Schema.unionOf(Schema.of(Schema.Type.STRING),
                                                                  Schema.of(Schema.Type.NULL))),
    Schema.Field.of(ErrorDataset.INVALIDENTRY, Schema.of(Schema.Type.STRING))
  );
  public static final String MDC_STAGE_KEY = "pipeline.stage";

  private Constants() {
    throw new AssertionError("Suppress default constructor for noninstantiability");
  }

  /**
   * Constants related to error dataset used in transform
   */
  public static final class ErrorDataset {
    public static final String ERRCODE = "errCode";
    public static final String ERRMSG = "errMsg";
    public static final String TIMESTAMP = "errTimestamp";
    public static final String INVALIDENTRY = "invalidRecord";
  }

  /**
   * Various metric constants.
   */
  public static final class Metrics {
    public static final String TOTAL_TIME = "process.time.total";
    public static final String MIN_TIME = "process.time.min";
    public static final String MAX_TIME = "process.time.max";
    public static final String STD_DEV_TIME = "process.time.stddev";
    public static final String AVG_TIME = "process.time.avg";
  }
}
