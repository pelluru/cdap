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
import co.cask.http.AbstractHttpHandler;
import co.cask.http.HandlerContext;
import co.cask.http.HttpResponder;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.twill.filesystem.Location;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Http service handler that serves files in deployed jar without exploding the jar.
 */
public class IntactJarHttpHandler extends AbstractHttpHandler implements JarHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(IntactJarHttpHandler.class);

  private static final MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();

  private final Location jarLocation;
  private JarFile jarFile;
  private ServePathGenerator servePathGenerator;

  @Inject
  public IntactJarHttpHandler(@Assisted Location jarLocation) {
    this.jarLocation = jarLocation;
  }

  @Override
  public void init(HandlerContext context) {
    super.init(context);
    try {
      jarFile = new JarFile(new File(jarLocation.toURI()));

      Predicate<String> fileExists = new Predicate<String>() {
        @Override
        public boolean apply(String file) {
          return file != null && jarFile.getJarEntry(file) != null;
        }
      };

      servePathGenerator = new ServePathGenerator(Constants.Webapp.WEBAPP_DIR, fileExists);
    } catch (IOException e) {
      LOG.error("Got exception: ", e);
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void destroy(HandlerContext context) {
    try {
      jarFile.close();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public String getServePath(String hostHeader, String uri) {
    return servePathGenerator.getServePath(hostHeader, uri);
  }

  @GET
  @Path("**")
  public void serve(HttpRequest request, HttpResponder responder) {
    try {
      String path = request.getUri();
      if (path == null) {
        responder.sendStatus(HttpResponseStatus.NOT_FOUND);
        return;
      }

      if (path.startsWith("/") && path.length() > 1) {
        path = path.substring(1);
      }

      JarEntry jarEntry = jarFile.getJarEntry(path);
      if (jarEntry == null) {
        responder.sendStatus(HttpResponseStatus.NOT_FOUND);
        return;
      }

      InputStream in = jarFile.getInputStream(jarEntry);
      if (in == null) {
        // path is directory
        responder.sendStatus(HttpResponseStatus.FORBIDDEN);
        return;
      }

      try {
        responder.sendByteArray(HttpResponseStatus.OK, ByteStreams.toByteArray(in),
                                ImmutableMultimap.of(HttpHeaders.Names.CONTENT_TYPE,
                                                     mimeTypesMap.getContentType(path)));

      } finally {
        in.close();
      }
    } catch (Throwable t) {
      LOG.error("Got exception: ", t);
      responder.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
