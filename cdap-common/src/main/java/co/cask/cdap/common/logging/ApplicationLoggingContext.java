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

package co.cask.cdap.common.logging;

/**
 * Application logging context.
 */
public abstract class ApplicationLoggingContext extends NamespaceLoggingContext {
  public static final String TAG_APPLICATION_ID = ".applicationId";
  public static final String TAG_RUN_ID = ".runId";
  public static final String TAG_INSTANCE_ID = ".instanceId";

  /**
   * Constructs ApplicationLoggingContext.
   * @param namespaceId namespace id
   * @param applicationId application id
   * @param runId run id of the application
   */
  public ApplicationLoggingContext(String namespaceId, String applicationId, String runId) {
    super(namespaceId);
    setSystemTag(TAG_APPLICATION_ID, applicationId);
    setSystemTag(TAG_RUN_ID, runId);
  }

  protected void setInstanceId(String instanceId) {
    setSystemTag(TAG_INSTANCE_ID, instanceId);
  }

  @Override
  public String getLogPartition() {
    return super.getLogPartition() + String.format(":%s", getSystemTag(TAG_APPLICATION_ID));
  }

  @Override
  public String getLogPathFragment(String logBaseDir) {
    return String.format("%s/%s", super.getLogPathFragment(logBaseDir), getSystemTag(TAG_APPLICATION_ID));
  }
}
