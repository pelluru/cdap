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

package co.cask.cdap.cli.util;

import co.cask.cdap.cli.CLIConnectionConfig;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.proto.id.NamespaceId;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class InstanceURIParserTest {

  @Test
  public void testParse() {
    CConfiguration cConf = CConfiguration.create();
    int defaultSSLPort = cConf.getInt(Constants.Router.ROUTER_SSL_PORT);
    int defaultPort = cConf.getInt(Constants.Router.ROUTER_PORT);
    NamespaceId someNamespace = new NamespaceId("nsx");
    InstanceURIParser parser = new InstanceURIParser(cConf);

    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", defaultPort, false),
                        parser.parse("somehost"));
    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", defaultPort, false),
                        parser.parse("http://somehost"));
    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", defaultSSLPort, true),
                        parser.parse("https://somehost"));

    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", 1234, false),
                        parser.parse("somehost:1234"));
    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", 1234, false),
                        parser.parse("http://somehost:1234"));
    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", 1234, true),
                        parser.parse("https://somehost:1234"));

    Assert.assertEquals(new CLIConnectionConfig(someNamespace, "somehost", 1234, false),
                        parser.parse("somehost:1234/nsx"));
    Assert.assertEquals(new CLIConnectionConfig(someNamespace, "somehost", 1234, false),
                        parser.parse("http://somehost:1234/nsx"));
    Assert.assertEquals(new CLIConnectionConfig(someNamespace, "somehost", 1234, true),
                        parser.parse("https://somehost:1234/nsx"));
  }

  @Test
  public void testParseTrailingSlash() {
    CConfiguration cConf = CConfiguration.create();
    InstanceURIParser parser = new InstanceURIParser(cConf);

    Assert.assertEquals(new CLIConnectionConfig(NamespaceId.DEFAULT, "somehost", 1234, true),
                        parser.parse("https://somehost:1234/"));
  }

}
