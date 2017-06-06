/*
 * Copyright © 2014-2016 Cask Data, Inc.
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
package co.cask.cdap.common.http;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.security.spi.authentication.SecurityRequestContext;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An UpstreamHandler that verifies the userId in a request header and updates the {@code SecurityRequestContext}.
 */
public class AuthenticationChannelHandler extends SimpleChannelUpstreamHandler {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationChannelHandler.class);

  private String currentUserId;
  private String currentUserIP;

  /**
   * Decode the AccessTokenIdentifier passed as a header and set it in a ThreadLocal.
   * Returns a 401 if the identifier is malformed.
   */
  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    if (message instanceof HttpRequest) {
      // TODO: authenticate the user using user id - CDAP-688
      HttpRequest request = (HttpRequest) message;
      currentUserId = request.getHeader(Constants.Security.Headers.USER_ID);
      currentUserIP = request.getHeader(Constants.Security.Headers.USER_IP);
      SecurityRequestContext.setUserId(currentUserId);
      SecurityRequestContext.setUserIP(currentUserIP);
    } else if (message instanceof HttpChunk) {
      SecurityRequestContext.setUserId(currentUserId);
      SecurityRequestContext.setUserIP(currentUserIP);
    }

    super.messageReceived(ctx, e);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
    LOG.error("Got exception: ", e.getCause());
    ChannelFuture future = Channels.future(ctx.getChannel());
    future.addListener(ChannelFutureListener.CLOSE);
    // TODO: add WWW-Authenticate header for 401 response -  REACTOR-900
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
    Channels.write(ctx, future, response);
  }
}
