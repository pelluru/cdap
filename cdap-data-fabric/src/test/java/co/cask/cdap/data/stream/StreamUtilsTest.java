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

package co.cask.cdap.data.stream;

import co.cask.cdap.data2.util.TableId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.StreamId;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link StreamUtils}.
 */
public class StreamUtilsTest {

  @Test
  public void testValidPartition() {
    Assert.assertTrue(StreamUtils.isPartition("00012345.00345"));
    Assert.assertTrue(StreamUtils.isPartition("1.1"));

    Assert.assertFalse(StreamUtils.isPartition(".1"));
    Assert.assertFalse(StreamUtils.isPartition("123."));

    Assert.assertFalse(StreamUtils.isPartition("12345"));
    Assert.assertFalse(StreamUtils.isPartition("abc.123"));
  }

  @Test
  public void testStreamIdFromLocation() {
    LocationFactory locationFactory = new LocalLocationFactory();
    String path = "/cdap/namespaces/default/streams/fooStream";
    Location streamBaseLocation = locationFactory.create(path);
    StreamId expectedId = NamespaceId.DEFAULT.stream("fooStream");
    Assert.assertEquals(expectedId, new StreamId("default",
                                                 StreamUtils.getStreamNameFromLocation(streamBaseLocation)));


    path = "/customLocation/streams/otherstream";
    streamBaseLocation = locationFactory.create(path);
    expectedId = new StreamId("othernamespace", "otherstream");
    Assert.assertEquals(expectedId, new StreamId("othernamespace",
                                                 StreamUtils.getStreamNameFromLocation(streamBaseLocation)));
  }

  @Test
  public void testGetStateStoreTableId() {
    NamespaceId namespace = new NamespaceId("foonamespace");
    TableId expected = TableId.from("foonamespace", "system.stream.state.store");
    Assert.assertEquals(expected, StreamUtils.getStateStoreTableId(namespace));
  }
}
