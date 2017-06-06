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

package co.cask.cdap.api.dataset.lib;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Types and methods to specify partitioning keys for datasets.
 */
public class PartitionKey {

  private final Map<String, Comparable> fields;

  /**
   * Private constructor to force use of the builder.
   */
  private PartitionKey(@Nonnull Map<String, Comparable> fields) {
    this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * @return all field names and their values in a map.
   */
  public Map<String, Comparable> getFields() {
    return fields;
  }

  /**
   * @return the value of a field
   */
  public Comparable getField(String fieldName) {
    return fields.get(fieldName);
  }

  @Override
  public String toString() {
    return fields.toString();
  }

  @Override
  public boolean equals(Object other) {
    return this == other ||
      (other != null && getClass() == other.getClass()
        && getFields().equals(((PartitionKey) other).getFields())); // fields is never null
  }

  @Override
  public int hashCode() {
    return fields.hashCode(); // fields is never null
  }

  /**
   * @return a builder for a partition key.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return a builder for a partition key with a known partitioning. This builder will
   *         only accept fields that are defined in the partitioning.
   */
  public static Builder builder(Partitioning partitioning) {
    return new Builder(partitioning);
  }

  /**
   * A builder for partitioning objects.
   */
  public static class Builder {

    private final LinkedHashMap<String, Comparable> fields = new LinkedHashMap<>();
    private final Partitioning partitioning;

    public Builder() {
      this(null);
    }

    private Builder(@Nullable Partitioning partitioning) {
      this.partitioning = partitioning;
    }

    /**
     * Add a field with a given name and value.
     *
     * @param name the field name
     * @param value the value of the field
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists,
     *         if the value is null, or if the partitioning is known and does not contain the field name,
     *         or the field is defined with a different type.
     */
    public Builder addField(String name, Comparable value) {
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty.");
      }
      if (value == null) {
        throw new IllegalArgumentException("Field value cannot be null.");
      }
      if (fields.containsKey(name)) {
        throw new IllegalArgumentException(String.format("Field '%s' already exists in partition key.", name));
      }
      if (partitioning != null) {
        if (!partitioning.getFields().containsKey(name)) {
          throw new IllegalArgumentException(String.format("Field '%s' is an unknown field in partitioning %s",
                                                           name, partitioning));
        }
        try {
          partitioning.getFields().get(name).validate(value);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(String.format(
            "Value for field '%s' is incompatible with the partitioning: %s", name, e.getMessage()));
        }
      }
      fields.put(name, value);
      return this;
    }

    /**
     * Add field of type STRING.
     *
     * @param name the field name
     * @param value the value of the field
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists,
     *         if the value is null, or if the partitioning is known and does not contain the field name,
     *         or the field is defined with a different type.
     */
    public Builder addStringField(String name, String value) {
      return addField(name, value);
    }

    /**
     * Add field of type INT.
     *
     * @param name the field name
     * @param value the value of the field
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists,
     *         if the value is null, or if the partitioning is known and does not contain the field name,
     *         or the field is defined with a different type.
     */
    public Builder addIntField(String name, int value) {
      return addField(name, value);
    }

    /**
     * Add field of type LONG.
     *
     * @param name the field name
     * @param value the value of the field
     *
     * @throws java.lang.IllegalArgumentException if the field name is null, empty, or already exists,
     *         if the value is null, or if the partitioning is known and does not contain the field name,
     *         or the field is defined with a different type.
     */
    public Builder addLongField(String name, long value) {
      return addField(name, value);
    }

    /**
     * Create the partition key.
     *
     * @throws IllegalStateException if no fields have been added,
     *         or the partitioning is known and not all fields have been added.
     */
    public PartitionKey build() {
      if (fields.isEmpty()) {
        throw new IllegalStateException("Partition key cannot be empty.");
      }
      if (partitioning != null && !partitioning.getFields().keySet().equals(fields.keySet())) {
        throw new IllegalStateException(String.format(
          "Partition key is incomplete: It only contains fields %s, but the partitioning requires %s",
          fields.keySet(), partitioning.getFields().keySet()));
      }
      return new PartitionKey(fields);
    }
  }
}
