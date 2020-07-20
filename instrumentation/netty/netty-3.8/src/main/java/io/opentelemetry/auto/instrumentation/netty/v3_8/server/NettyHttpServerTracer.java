/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.netty.v3_8.server;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.auto.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class NettyHttpServerTracer
    extends HttpServerTracer<HttpRequest, Channel, ChannelTraceContext> {
  public static final NettyHttpServerTracer TRACER = new NettyHttpServerTracer();

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  protected void attachServerContext(Context context, ChannelTraceContext channelTraceContext) {
    channelTraceContext.setContext(context);
  }

  @Override
  public Context getServerContext(ChannelTraceContext channelTraceContext) {
    return channelTraceContext.getContext();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected String peerHostIP(final Channel channel) {
    SocketAddress socketAddress = channel.getRemoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return NettyRequestExtractAdapter.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.netty-3.8";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected Integer peerPort(final Channel channel) {
    SocketAddress socketAddress = channel.getRemoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }
}
