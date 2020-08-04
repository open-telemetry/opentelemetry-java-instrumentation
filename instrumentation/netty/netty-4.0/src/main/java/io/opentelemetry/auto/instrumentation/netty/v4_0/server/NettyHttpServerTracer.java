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

package io.opentelemetry.auto.instrumentation.netty.v4_0.server;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.grpc.Context;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.auto.instrumentation.netty.v4_0.AttributeKeys;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.library.api.decorator.HttpServerTracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

public class NettyHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, Channel, Channel> {
  public static final NettyHttpServerTracer TRACER = new NettyHttpServerTracer();

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }

  @Override
  protected void attachServerContext(Context context, Channel channel) {
    channel.attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).set(context);
  }

  @Override
  public Context getServerContext(Channel channel) {
    return channel.attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.netty-4.0";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return NettyRequestExtractAdapter.GETTER;
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
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected String flavor(Channel channel, HttpRequest request) {
    return request.getProtocolVersion().toString();
  }

  @Override
  protected Integer peerPort(final Channel channel) {
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }
}
