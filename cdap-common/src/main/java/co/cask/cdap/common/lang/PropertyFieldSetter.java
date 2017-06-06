/*
 * Copyright © 2014 Cask Data, Inc.
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
package co.cask.cdap.common.lang;

import co.cask.cdap.api.annotation.Property;
import co.cask.cdap.internal.lang.FieldVisitor;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link FieldVisitor} that sets field value based on a given property map.
 */
public final class PropertyFieldSetter extends FieldVisitor {

  private final Map<String, String> properties;

  public PropertyFieldSetter(Map<String, String> properties) {
    this.properties = properties;
  }

  @Override
  public void visit(Object instance, Type inspectType, Type declareType, Field field) throws Exception {
    if (field.isAnnotationPresent(Property.class)) {
      String key = TypeToken.of(declareType).getRawType().getName() + '.' + field.getName();
      String value = properties.get(key);
      if (value == null) {
        return;
      }
      setValue(instance, field, value);
    }
  }

  /**
   * Sets the value of the field in the given instance by converting the value from String to the field type.
   * Currently only allows primitive types, boxed types, String and Enum.
   */
  @SuppressWarnings("unchecked")
  private void setValue(Object instance, Field field, String value) throws IllegalAccessException {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }

    Class<?> fieldType = field.getType();

    // Currently only String, primitive (or boxed type) and Enum type are supported.
    if (String.class.equals(fieldType)) {
      field.set(instance, value);
      return;
    }
    if (fieldType.isEnum()) {
      field.set(instance, Enum.valueOf((Class<? extends Enum>) fieldType, value));
      return;
    }

    if (fieldType.isPrimitive()) {
      fieldType = com.google.common.primitives.Primitives.wrap(fieldType);
    }

    try {
      // All box type has the valueOf(String) method.
      field.set(instance, fieldType.getMethod("valueOf", String.class).invoke(null, value));
    } catch (NoSuchMethodException e) {
      // Should never happen, as boxed type always have the valueOf(String) method.
      throw Throwables.propagate(e);
    } catch (InvocationTargetException e) {
      // Also should never happen, as calling method on Java bootstrap classes should always succeed.
      throw Throwables.propagate(e);
    }
  }
}
