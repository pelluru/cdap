/*
 * Copyright © 2016 Cask Data, Inc.
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

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.etl.api.Aggregator;
import co.cask.cdap.etl.api.Joiner;
import com.google.common.reflect.TypeToken;

/**
 * Helper for checking parameter types.
 */
public class TypeChecker {

  private TypeChecker() {
  }

  public static Class<?> getGroupKeyClass(Aggregator aggregator) {
    return getParameterClass(aggregator, Aggregator.class, 0);
}

  public static Class<?> getGroupValueClass(Aggregator aggregator) {
    return getParameterClass(aggregator, Aggregator.class, 1);
  }

  public static Class<?> getJoinKeyClass(Joiner joiner) {
    return getParameterClass(joiner, Joiner.class, 0);
  }

  public static Class<?> getJoinInputRecordClass(Joiner joiner) {
    return getParameterClass(joiner, Joiner.class, 1);
  }

  public static Class getParameterClass(Object instance, Class instanceClass, int parameterNumber) {
    return TypeToken.of(instance.getClass())
      .resolveType(instanceClass.getTypeParameters()[parameterNumber]).getRawType();
  }

  public static boolean groupKeyIsStructuredRecord(Aggregator aggregator) {
    return getParameterClass(aggregator, Aggregator.class, 0) == StructuredRecord.class;
  }

  public static boolean groupValueIsStructuredRecord(Aggregator aggregator) {
    return getParameterClass(aggregator, Aggregator.class, 1) == StructuredRecord.class;
  }
}
