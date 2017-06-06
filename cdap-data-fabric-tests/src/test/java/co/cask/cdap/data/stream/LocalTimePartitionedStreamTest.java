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
package co.cask.cdap.data.stream;

import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.BeforeClass;

import java.io.IOException;

/**
 *
 */
public class LocalTimePartitionedStreamTest extends TimePartitionedStreamTestBase {

  private static LocationFactory locationFactory;

  @BeforeClass
  public static void init() throws IOException {
    locationFactory = new LocalLocationFactory(tmpFolder.newFolder());
  }

  @Override
  protected LocationFactory getLocationFactory() {
    return locationFactory;
  }
}
