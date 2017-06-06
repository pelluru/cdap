/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

package co.cask.cdap.hive.context;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.io.Codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Codec to encode/decode CConfiguration object.
 */
public class CConfCodec implements Codec<CConfiguration> {
  public static final CConfCodec INSTANCE = new CConfCodec();

  private CConfCodec() {
    // Use the static INSTANCE to get an instance.
  }

  @Override
  public byte[] encode(CConfiguration object) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    object.writeXml(bos);
    bos.close();
    return bos.toByteArray();
  }

  @Override
  public CConfiguration decode(byte[] data) throws IOException {
    if (data == null) {
      return CConfiguration.create();
    }

    ByteArrayInputStream bin = new ByteArrayInputStream(data);
    return CConfiguration.create(bin);
  }
}
