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

package co.cask.cdap.format;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.table.Put;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * Test cases for {@link RecordPutTransformer}.
 */
@SuppressWarnings("unchecked")
public class RecordPutTransformerTest {

  @Test(expected = IllegalArgumentException.class)
  public void testNullRowkeyThrowsException() throws Exception {
    Schema schema = Schema.recordOf("record", Schema.Field.of("key", Schema.nullableOf(Schema.of(Schema.Type.STRING))));
    RecordPutTransformer transformer = new RecordPutTransformer("key", schema);

    StructuredRecord record = StructuredRecord.builder(schema).build();
    transformer.toPut(record);
  }

  @Test
  public void testNullableFields() throws Exception {
    Schema schema = Schema.recordOf(
      "record",
      Schema.Field.of("key", Schema.of(Schema.Type.INT)),
      Schema.Field.of("nullable", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("non_nullable", Schema.of(Schema.Type.STRING))
    );
    RecordPutTransformer transformer = new RecordPutTransformer("key", schema);

    // valid record
    StructuredRecord record = StructuredRecord.builder(schema)
      .set("key", 1)
      .set("non_nullable", "foo")
      .build();

    Put transformed = transformer.toPut(record);

    Assert.assertEquals(1, Bytes.toInt(transformed.getRow()));
    // expect a null value for the nullable field
    Assert.assertEquals(2, transformed.getValues().size());
    Assert.assertEquals("foo", Bytes.toString(transformed.getValues().get(Bytes.toBytes("non_nullable"))));
    Assert.assertNull(transformed.getValues().get(Bytes.toBytes("nullable")));
  }

  @Test
  public void testNullableSchema() throws Exception {
    // tests that null can be passed in for the schema (in which case it will pickup the schema from the record
    Schema schema = Schema.recordOf(
      "record",
      Schema.Field.of("key", Schema.of(Schema.Type.INT)),
      Schema.Field.of("nullable", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("non_nullable", Schema.of(Schema.Type.STRING))
    );
    RecordPutTransformer transformer = new RecordPutTransformer("key", null);

    // valid record
    StructuredRecord record = StructuredRecord.builder(schema)
      .set("key", 1)
      .set("non_nullable", "foo")
      .build();

    Put transformed = transformer.toPut(record);

    Assert.assertEquals(1, Bytes.toInt(transformed.getRow()));
    // expect a null value for the nullable field
    Assert.assertEquals(2, transformed.getValues().size());
    Assert.assertEquals("foo", Bytes.toString(transformed.getValues().get(Bytes.toBytes("non_nullable"))));
    Assert.assertNull(transformed.getValues().get(Bytes.toBytes("nullable")));
  }

  @Test
  public void testTransform() throws Exception {
    Schema schema = Schema.recordOf(
      "record",
      Schema.Field.of("boolField", Schema.nullableOf(Schema.of(Schema.Type.BOOLEAN))),
      Schema.Field.of("intField", Schema.nullableOf(Schema.of(Schema.Type.INT))),
      Schema.Field.of("longField", Schema.nullableOf(Schema.of(Schema.Type.LONG))),
      Schema.Field.of("floatField", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
      Schema.Field.of("doubleField", Schema.nullableOf(Schema.of(Schema.Type.DOUBLE))),
      Schema.Field.of("bytesField", Schema.nullableOf(Schema.of(Schema.Type.BYTES))),
      Schema.Field.of("stringField", Schema.of(Schema.Type.STRING))
    );
    RecordPutTransformer transformer = new RecordPutTransformer("stringField", schema);

    StructuredRecord record = StructuredRecord.builder(schema)
      .set("boolField", true)
      .set("intField", 5)
      .set("longField", 10L)
      .set("floatField", 3.14f)
      .set("doubleField", 3.14)
      .set("bytesField", Bytes.toBytes("foo"))
      .set("stringField", "key")
      .build();

    Put transformed = transformer.toPut(record);

    Assert.assertEquals("key", Bytes.toString(transformed.getRow()));
    Map<byte[], byte[]> values = transformed.getValues();
    Assert.assertTrue(Bytes.toBoolean(values.get(Bytes.toBytes("boolField"))));
    Assert.assertEquals(5, Bytes.toInt(values.get(Bytes.toBytes("intField"))));
    Assert.assertEquals(10L, Bytes.toLong(values.get(Bytes.toBytes("longField"))));
    Assert.assertTrue(Math.abs(3.14f - Bytes.toFloat(values.get(Bytes.toBytes("floatField")))) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - Bytes.toDouble(values.get(Bytes.toBytes("doubleField")))) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), values.get(Bytes.toBytes("bytesField")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRowKeyNotPresent() {
    Schema schema = Schema.recordOf("record",
                                    Schema.Field.of("KEY", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("Key", Schema.nullableOf(Schema.of(Schema.Type.BYTES))));
    RecordPutTransformer transformer = new RecordPutTransformer("key", schema);
    StructuredRecord record = StructuredRecord.builder(schema).set("KEY", "someKey").set("Key", "someOtherKey").build();
    transformer.toPut(record);
  }

  @Test
  public void testOutputSchemaUsage() {
    Schema outputSchema = Schema.recordOf("output",
                                          Schema.Field.of("id", Schema.of(Schema.Type.LONG)),
                                          Schema.Field.of("name", Schema.of(Schema.Type.STRING)));
    Schema inputSchema = Schema.recordOf("input",
                                         Schema.Field.of("id", Schema.of(Schema.Type.LONG)),
                                         Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                         Schema.Field.of("age", Schema.of(Schema.Type.INT)));
    StructuredRecord record = StructuredRecord.builder(inputSchema)
      .set("id", 123L).set("name", "ABC").set("age", 10).build();
    RecordPutTransformer transformer = new RecordPutTransformer("id", outputSchema);
    Put put = transformer.toPut(record);
    Assert.assertEquals(1, put.getValues().size());

    transformer = new RecordPutTransformer("id", inputSchema);
    put = transformer.toPut(record);
    Assert.assertEquals(2, put.getValues().size());
  }
}
