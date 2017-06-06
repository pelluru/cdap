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

package co.cask.cdap.api.data.format;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.schema.Schema;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import javax.annotation.Nullable;

/**
 * Instance of a record structured by a {@link Schema}. Fields are accessible by name.
 */
@Beta
public class StructuredRecord implements Serializable {
  private static final SimpleDateFormat DEFAULT_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss z");
  private final Schema schema;
  private final Map<String, Object> fields;

  private static final long serialVersionUID = -4648752378975451591L;

  {
    DEFAULT_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private StructuredRecord(Schema schema, Map<String, Object> fields) {
    this.schema = schema;
    this.fields = fields;
  }

  /**
   * Get the schema of the record.
   *
   * @return schema of the record.
   */
  public Schema getSchema() {
    return schema;
  }

  /**
   * Get the value of a field in the record.
   *
   * @param fieldName field to get.
   * @param <T> type of object of the field value.
   * @return value of the field.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String fieldName) {
    return (T) fields.get(fieldName);
  }

  /**
   * Get a builder for creating a record with the given schema.
   *
   * @param schema schema for the record to build.
   * @return builder for creating a record with the given schema.
   * @throws UnexpectedFormatException if the given schema is not a record with at least one field.
   */
  public static Builder builder(Schema schema) throws UnexpectedFormatException {
    if (schema == null || schema.getType() != Schema.Type.RECORD || schema.getFields().size() < 1) {
      throw new UnexpectedFormatException("Schema must be a record with at least one field.");
    }
    return new Builder(schema);
  }

  /**
   * Builder for creating a {@link StructuredRecord}.
   * TODO: enforce schema correctness?
   */
  public static class Builder {
    private final Schema schema;
    private Map<String, Object> fields;

    private Builder(Schema schema) {
      this.schema = schema;
      this.fields = new HashMap<>();
    }

    /**
     * Set the field to the given value.
     *
     * @param fieldName Name of the field to set
     * @param value Value for the field
     * @return This builder
     * @throws UnexpectedFormatException if the field is not in the schema, or the field is not nullable but a null
     *                                   value is given
     */
    public Builder set(String fieldName, @Nullable Object value) {
      validateAndGetField(fieldName, value);
      fields.put(fieldName, value);
      return this;
    }

    /**
     * Convert the given date into the type of the given field, and set the value for that field.
     * A Date can be converted into a long or a string.
     *
     * @param fieldName Name of the field to set
     * @param date Date value for the field
     * @return This builder
     * @throws UnexpectedFormatException if the field is not in the schema, or the field is not nullable but a null
     *                                   value is given, or the date cannot be converted to the type for the field
     */
    public Builder convertAndSet(String fieldName, @Nullable Date date) throws UnexpectedFormatException {
      return convertAndSet(fieldName, date, DEFAULT_FORMAT);
    }

    /**
     * Convert the given date into the type of the given field, and set the value for that field.
     * A Date can be converted into a long or a string, using a date format, if supplied.
     *
     * @param fieldName Name of the field to set
     * @param date Date value for the field
     * @param dateFormat Format for the date if it is a string. If null, the default format of
     *                   "YYYY-MM-DD'T'HH:mm:ss z" will be used
     * @return This builder
     * @throws UnexpectedFormatException if the field is not in the schema, or the field is not nullable but a null
     *                                   value is given, or the date cannot be converted to the type for the field
     */
    public Builder convertAndSet(String fieldName, @Nullable Date date,
                                 @Nullable DateFormat dateFormat) throws UnexpectedFormatException {
      Schema.Field field = validateAndGetField(fieldName, date);
      boolean isNullable = field.getSchema().isNullable();
      if (isNullable && date == null) {
        fields.put(fieldName, null);
        return this;
      }

      Schema.Type fieldType = isNullable ? field.getSchema().getNonNullable().getType() : field.getSchema().getType();
      if (fieldType == Schema.Type.LONG) {
        fields.put(fieldName, date.getTime());
      } else if (fieldType == Schema.Type.STRING) {
        DateFormat format = dateFormat == null ? DEFAULT_FORMAT : dateFormat;
        fields.put(fieldName, format.format(date));
      } else {
        throw new UnexpectedFormatException("Date must be either a long or a string, not a " + fieldType);
      }
      return this;
    }

    /**
     * Convert the given string into the type of the given field, and set the value for that field. A String can be
     * converted to a boolean, int, long, float, double, bytes, string, or null.
     *
     * @param fieldName Name of the field to set
     * @param strVal String value for the field
     * @return This builder
     * @throws UnexpectedFormatException if the field is not in the schema, or the field is not nullable but a null
     *                                   value is given, or the string cannot be converted to the type for the field
     */
    public Builder convertAndSet(String fieldName, @Nullable String strVal) throws UnexpectedFormatException {
      Schema.Field field = validateAndGetField(fieldName, strVal);
      fields.put(fieldName, convertString(field.getSchema(), strVal));
      return this;
    }

    /**
     * Build a {@link StructuredRecord} with the fields set by this builder.
     *
     * @return A {@link StructuredRecord} with the fields set by this builder
     * @throws UnexpectedFormatException if there is at least one non-nullable field without a value
     */
    public StructuredRecord build() throws UnexpectedFormatException {
      // check that all non-nullable fields have a value.
      for (Schema.Field field : schema.getFields()) {
        String fieldName = field.getName();
        if (!fields.containsKey(fieldName)) {
          // if the field is not nullable and there is no value set for the field, this is invalid.
          if (!field.getSchema().isNullable()) {
            throw new UnexpectedFormatException("Field " + fieldName + " must contain a value.");
          } else {
            // otherwise, set the value for the field to null
            fields.put(fieldName, null);
          }
        }
      }
      return new StructuredRecord(schema, fields);
    }

    private Object convertString(Schema schema, String strVal) throws UnexpectedFormatException {
      Schema.Type simpleType;
      if (schema.getType().isSimpleType()) {
        simpleType = schema.getType();
        if (strVal == null && simpleType != Schema.Type.NULL) {
          throw new UnexpectedFormatException("Cannot set non-nullable field to a null value.");
        }
      } else if (schema.isNullable()) {
        if (strVal == null) {
          return null;
        }
        simpleType = schema.getNonNullable().getType();
      } else {
        throw new UnexpectedFormatException("Cannot convert a string to schema " + schema);
      }

      switch (simpleType) {
        case BOOLEAN:
          return Boolean.parseBoolean(strVal);
        case INT:
          return Integer.parseInt(strVal);
        case LONG:
          return Long.parseLong(strVal);
        case FLOAT:
          return Float.parseFloat(strVal);
        case DOUBLE:
          return Double.parseDouble(strVal);
        case BYTES:
          return Bytes.toBytesBinary(strVal);
        case STRING:
          return strVal;
        case NULL:
          return null;
        default:
          // shouldn't ever get here
          throw new UnexpectedFormatException("Cannot convert a string to schema " + schema);
      }
    }

    private Schema.Field validateAndGetField(String fieldName, Object val) {
      Schema.Field field = schema.getField(fieldName);
      if (field == null) {
        throw new UnexpectedFormatException("field " + fieldName + " is not in the schema.");
      }
      if (!field.getSchema().isNullable() && val == null) {
        throw new UnexpectedFormatException("field " + fieldName + " cannot be set to a null value.");
      }
      return field;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StructuredRecord that = (StructuredRecord) o;

    return Objects.equals(schema, that.schema) && Objects.equals(fields, that.fields);

  }

  @Override
  public int hashCode() {
    return Objects.hash(schema, fields);
  }
}
