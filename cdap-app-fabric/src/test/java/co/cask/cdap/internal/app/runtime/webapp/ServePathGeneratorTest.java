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

package co.cask.cdap.internal.app.runtime.webapp;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.lang.jar.JarResources;
import com.google.common.base.Predicate;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import javax.annotation.Nullable;

/**
 * Tests ServePathGenerator.
 */
public class ServePathGeneratorTest {
  @Test
  public void testGetServePath() throws Exception {
    URL jarUrl = getClass().getResource("/CountRandomWebapp-localhost.jar");
    Assert.assertNotNull(jarUrl);

    final JarResources jarResources = new JarResources(new LocalLocationFactory().create(jarUrl.toURI()));
    Predicate<String> fileExists = new Predicate<String>() {
      @Override
      public boolean apply(@Nullable String file) {
        return file != null && jarResources.getResource(file) != null;
      }
    };

    ServePathGenerator servePathGenerator = new ServePathGenerator(Constants.Webapp.WEBAPP_DIR, fileExists);

    Assert.assertEquals("/webapp/127.0.0.1:20000/netlens/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:20000", "/netlens"));

    Assert.assertEquals("/webapp/127.0.0.1:20000/netlens/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:20000", "/netlens/index.html"));

    Assert.assertEquals("/webapp/127.0.0.1:20000/netlens/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:20000", "/netlens/"));

    Assert.assertEquals("/webapp/127.0.0.1:20000/netlens/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:20000", "/netlens/"));

    Assert.assertEquals("/webapp/default/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/"));

    Assert.assertEquals("/webapp/default/src/index.html",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/index.html"));

    Assert.assertEquals("/webapp/127.0.0.1:20000/src/netlens/2.txt",
                        servePathGenerator.getServePath("127.0.0.1:20000", "netlens/2.txt"));

    Assert.assertEquals("/webapp/default/netlens/src/1.txt",
                        servePathGenerator.getServePath("127.0.0.1:80", "/netlens/1.txt?count=100"));

    Assert.assertEquals("/webapp/default/netlens/src/data/data.txt",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/netlens/data/data.txt"));

    Assert.assertEquals("/v3/apps?count=10",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/netlens/v3/apps?count=10"));

    Assert.assertEquals("/v3/apps?count=10",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/v3/apps?count=10"));

    Assert.assertEquals("/status",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/netlens/status"));

    Assert.assertEquals("/status",
                        servePathGenerator.getServePath("127.0.0.1:30000", "/status"));


    servePathGenerator = new ServePathGenerator(Constants.Webapp.WEBAPP_DIR + "/", fileExists);
    Assert.assertEquals("/webapp/www.abc.com:80/geo/src/data/data.txt",
                        servePathGenerator.getServePath("www.abc.com", "/geo/data/data.txt"));

    Assert.assertEquals("/webapp/www.abc.com:80/geo/src/data/data.txt",
                        servePathGenerator.getServePath("www.abc.com:80", "/geo/data/data.txt"));

    Assert.assertEquals("/webapp/default/netlens/src/data/data.txt",
                        servePathGenerator.getServePath("www.abc.com:30000", "/netlens/data/data.txt"));

    Assert.assertEquals("/geo/data/data.txt",
                        servePathGenerator.getServePath("www.abc.com:30000", "/geo/data/data.txt"));
  }
}
