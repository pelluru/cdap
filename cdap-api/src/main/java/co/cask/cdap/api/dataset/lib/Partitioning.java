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

package co.cask.cdap.api.dataset.lib;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * This class describes how a dataset is partitioned, by means of the fields of a partition key and their types.
 * The ordering of fields in the partitioning matters: It is the order in which partition keys are indexed in the
 * meta data. As a best practice, a Partitioning should name the fields in the order of how frequently they are
 * used in partition filters, because partition filters that contain a condition for the first field in the
 * Partitioning perform best.
 */
public class Partitioning {

  /**
   * Describes the type of a partitioning field.
   */
  public enum FieldType {
    STRING {
      @Override
      public String parse(String value) {
        return value;
      }

      @Override
      public void validate(Comparable value) {
        validate(value, String.class);
      }
    },
    LONG {
      @Override
      public Long parse(String value) {
        return Long.parseLong(value);
      }

      @Override
      public void validate(Comparable value) {
        validate(value, Long.class);
      }
    },
    INT {
      @Override
      public Integer parse(String value) {
        return Integer.parseInt(value);
      }

      @Override
      public void validate(Comparable value) {
        validate(value, Integer.class);
      }
    };

    /**
     * Parse a string into a value of this field type. For example, {@link FieldType#INT} delegates this
     * to {@link Integer#parseInt}.
     * @param value the string to parse
     */
    public abstract Comparable parse(String value);

    /**
     * Validate that a value has the correct type for this field type.
     * @param value the value to validate
     * @throws IllegalArgumentException if the value is of wrong type.
     */
    public abstract void validate(Comparable value);

    protected void validate(Comparable value, Class<? extends Comparable> expectedClass) {
      if (!expectedClass.equals(value.getClass())) {
        throw new IllegalArgumentException(String.format("Value %s of type %s cannot be assigned to a %s field",
                                                         value, value.getClass().getSimpleName(), this));
      }
    }
  }

  private final Map<String, FieldType> fields;

  /**
   * Private constructor to force the use of the builder.
   */
  private Partitioning(LinkedHashMap<String, FieldType> fields) {
    this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * @return the type of a field, or null if that field is not declared for the partitioning
   */
  public FieldType getFieldType(String fieldName) {
    return fields.get(fieldName);
  }

  /**
   * This returns a map associating all the fields of this partitioning with their respective types. Iterators
   * over the key set or the entry set of this map will yield the same order in which the fields were added to
   * the partitioning.
   *
   * @return all fields and their types
   */
  public Map<String, FieldType> getFields() {
    return fields;
  }

  @Override
  public String toString() {
    return fields.toString();
  }

  /**
   * @return a builder for a partitioning
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder for partitioning objects.
   */
  public static class Builder {

    private final LinkedHashMap<String, FieldType> fields = new LinkedHashMap<>();

    private Builder() { }

    /**
     * Add a field with a given name and type.
     *
     * @param name the field name
     * @param type the type of the field
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists,
     *         or if the type is null.
     */
    @SuppressWarnings("ConstantConditions")
    public Builder addField(@Nonnull String name, @Nonnull FieldType type) {
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty.");
      }
      if (type == null) {
        throw new IllegalArgumentException("Field type cannot be null.");
      }
      if (fields.containsKey(name)) {
        throw new IllegalArgumentException(String.format("Field '%s' already exists in partitioning.", name));
      }
      fields.put(name, type);
      return this;
    }

    /**
     * Add field of type STRING.
     *
     * @param name the field name
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists.
     */
    public Builder addStringField(String name) {
      return addField(name, FieldType.STRING);
    }

    /**
     * Add field of type INT.
     *
     * @param name the field name
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists.
     */
    public Builder addIntField(String name) {
      return addField(name, FieldType.INT);
    }

    /**
     * Add field of type LONG.
     *
     * @param name the field name
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists.
     */
    public Builder addLongField(String name) {
      return addField(name, FieldType.LONG);
    }

    /**
     * Create the partitioning.
     *
     * @throws java.lang.IllegalStateException if no fields have been added
     */
    public Partitioning build() {
      if (fields.isEmpty()) {
        throw new IllegalStateException("Partitioning cannot be empty.");
      }
      return new Partitioning(fields);
    }
  }

}
