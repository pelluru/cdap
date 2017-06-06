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

/**
 * This package contains internal classes for supporting the CDAP API.
 * <p>
 * These should not be used directly by users of the CDAP API, as they may change in a later release without warning.
 * </p>
 *
 * <h3>Schema definition</h3>
 *
 * The schema definition is adopted from <a href="http://avro.apache.org/docs/1.7.3/spec.html#schemas">Avro Schema</a>,
 * with the following modifications:
 *
 * <ol>
 *   <li>Supports any type as map key, not just string.</li>
 *   <li>No "name" property for enum type.</li>
 *   <li>No support of "doc" and "aliases" in record and enum types.</li>
 *   <li>No support of "doc" and "default" in record field.</li>
 *   <li>Dropped the "fixed" type.</li>
 * </ol>
 *
 * <h4>Primitive types</h4>
 * <table border="1" cellpadding="3">
 *   <tr>
 *     <th>Schema</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td><code>"null"</code> or <code>{"type":"null"}</code></td>
 *     <td>No value</td>
 *   </tr>
 *   <tr>
 *     <td><code>"boolean"</code> or <code>{"type":"boolean"}</code></td>
 *     <td>Boolean (<code>true</code> or <code>false</code>) value</td>
 *   </tr>
 *   <tr>
 *     <td><code>"int"</code> or <code>{"type":"int"}</code></td>
 *     <td>Signed 32-bit integer</td>
 *   </tr>
 *   <tr>
 *     <td><code>"long"</code> or <code>{"type":"long"}</code></td>
 *     <td>Signed 64-bit integer</td>
 *   </tr>
 *   <tr>
 *     <td><code>"float"</code> or <code>{"type":"float"}</code></td>
 *     <td>Single precision IEEE-754 floating point number</td>
 *   </tr>
 *   <tr>
 *     <td><code>"double"</code> or <code>{"type":"double"}</code></td>
 *     <td>Double precision IEEE-754 floating point number</td>
 *   </tr>
 *   <tr>
 *     <td><code>"string"</code> or <code>{"type":"string"}</code></td>
 *     <td>Unicode character sequence</td>
 *   </tr>
 *   <tr>
 *     <td><code>"bytes"</code> or <code>{"type":"bytes"}</code></td>
 *     <td>Sequence of octets</td>
 *   </tr>
 * </table>
 *
 * <h4>Complex types</h4>
 * <table border="1" cellpadding="3" style="width:100%">
 *   <tr>
 *     <th>Type</th>
 *     <th>Schema</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td style="width:10%">Enum</td>
 *     <td style="width:45%"><code>{"type":"enum","symbols":["SUCCESS","FAILURE]}</code></td>
 *     <td style="width:45%">List of string symbols</td>
 *   </tr>
 *   <tr>
 *     <td>Array</td>
 *     <td><code>{"type":"array","items":<i>&lt;schema of array item type&gt;</i>}</code></td>
 *     <td>List of items of the same type, with the item schema defined in the <code>"items"</code> property</td>
 *   </tr>
 *   <tr>
 *     <td>Map</td>
 *     <td>
 *       <code>
 *         {"type":"map","keys":<i>&lt;schema of key type&gt;</i>,"values":<i>&lt;schema of value type&gt;</i>}
 *       </code>
 *     </td>
 *     <td>Map from the same key type to the same value type</td>
 *   </tr>
 *   <tr>
 *     <td>Record</td>
 *     <td><code>
 *       {"type":"record",
 *        "name":<i>&lt;record name&gt;</i>,
 *        "fields":[<br/>
 *          {"name":<i>&lt;field name&gt;</i>,"type":<i>&lt;schema of the record field&gt;</i>},<br/>
 *          ...<br/>
 *        ]}
 *     </code><br/>
 *     or<br/>
 *     <code>"<i>&lt;record name&gt;</i>"</code></td>
 *     <td>
 *       Record that contains list of fields. The <code>"name"</code> property defines name of the record, which
 *       could be used to define recursive data structure (such as linked list). The <code>"fields"</code> property
 *       is used for defining field name and schema for each field in the record.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>Union</td>
 *     <td><code>[<i>&lt;schema of first type&gt</i>,<i>&lt;schema of second type&gt</i>,...]</code></td>
 *     <td>
 *       Represents an union of schemas
 *     </td>
 *   </tr>
 * </table>
 *
 * <h3>Schema Compatibility</h3>
 *
 * <table border="1" cellpadding="3">
 *   <tr>
 *     <th>Writer type</th>
 *     <th>Compatible reader types</th>
 *   </tr>
 *   <tr>
 *     <td><code>null</code></td>
 *     <td><code>null</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>boolean</code></td>
 *     <td><code>boolean, string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>int</code></td>
 *     <td><code>int, long, float, double, string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>long</code></td>
 *     <td><code>long, float, double, string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>float</code></td>
 *     <td><code>float, double, string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>double</code></td>
 *     <td><code>double, string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>string</code></td>
 *     <td><code>string</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>bytes</code></td>
 *     <td><code>bytes</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>array</code></td>
 *     <td><code>array</code> with compatible <code>items</code> schema.</td>
 *   </tr>
 *   <tr>
 *     <td><code>map</code></td>
 *     <td><code>map</code> with compatible <code>keys</code> and <code>values</code> schemas.</td>
 *   </tr>
 *   <tr>
 *     <td><code>record</code></td>
 *     <td>
 *       <code>record</code> with compatible schemas over common <code>fields</code>, matched by field name.<br/>
 *       It is allowed to have missing writer record fields in the reader schema and vice versa.
 *     </td>
 *   </tr>
 * </table>
 * 
 * <h4>For union types</h4>
 * <table border="1" cellpadding="3">
 *   <tr>
 *     <th>Is writer <code>union</code>?</th>
 *     <th>Is reader <code>union</code>?</th>
 *     <th>Compatible requirements</th>
 *   </tr>
 *   <tr>
 *     <td align="center">yes</td>
 *     <td align="center">yes</td>
 *     <td>At least one pair of schema between writer and reader unions must be compatible.</td>
 *   </tr>
 *   <tr>
 *     <td align="center">yes</td>
 *     <td align="center">no</td>
 *     <td>At least one schema in the writer union must be compatible with the reader schema.</td>
 *   </tr>
 *   <tr>
 *     <td align="center">no</td>
 *     <td align="center">yes</td>
 *     <td>Writer schema must be compatible with at least one schema in the reader union.</td>
 *   </tr>
 * </table>
 *
 */
package co.cask.cdap.internal.io;

