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

package co.cask.cdap.data2.metadata.dataset;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.data2.dataset2.lib.table.EntityIdKeyHelper;
import co.cask.cdap.data2.dataset2.lib.table.MDSKey;
import co.cask.cdap.proto.id.NamespacedEntityId;

/**
 * Key used to store metadata history.
 */
public class MdsHistoryKey {
  private static final byte[] ROW_PREFIX = {'h'};

  public static MDSKey getMdsKey(NamespacedEntityId targetId, long time) {
    MDSKey.Builder builder = new MDSKey.Builder();
    builder.add(ROW_PREFIX);
    EntityIdKeyHelper.addTargetIdToKey(builder, targetId);
    builder.add(invertTime(time));
    return builder.build();
  }

  public static MDSKey getMdsScanStartKey(NamespacedEntityId targetId, long time) {
    return getMdsKey(targetId, time);
  }

  public static MDSKey getMdsScanEndKey(NamespacedEntityId targetId) {
    MDSKey.Builder builder = new MDSKey.Builder();
    builder.add(ROW_PREFIX);
    EntityIdKeyHelper.addTargetIdToKey(builder, targetId);
    byte[] key = builder.build().getKey();
    return new MDSKey(Bytes.stopKeyForPrefix(key));
  }

  private static long invertTime(long time) {
    return Long.MAX_VALUE - time;
  }
}
