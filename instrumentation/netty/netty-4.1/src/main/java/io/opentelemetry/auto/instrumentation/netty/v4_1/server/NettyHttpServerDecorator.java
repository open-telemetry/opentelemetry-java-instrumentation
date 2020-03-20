/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.netty.v4_1.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHttpServerDecorator
    extends HttpServerDecorator<HttpRequest, Channel, HttpResponse> {
  public static final NettyHttpServerDecorator DECORATE = new NettyHttpServerDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.netty-4.1");

  @Override
  protected String getComponentName() {
    return "netty";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    final URI uri = new URI(request.uri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.uri());
    } else {
      return uri;
    }
  }

  @Override
  protected String peerHostIP(final Channel channel) {
    final SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected Integer peerPort(final Channel channel) {
    final SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.status().code();
  }
}
