/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.AbstractNettyRequestWrapper;

public class NettyRequestWrapper extends AbstractNettyRequestWrapper {
  private static final Class<? extends ChannelHandler> sslHandlerClass = getSslHandlerClass();

  @SuppressWarnings("unchecked")
  private static Class<? extends ChannelHandler> getSslHandlerClass() {
    try {
      return (Class<? extends ChannelHandler>)
          Class.forName(
              "io.netty.handler.ssl.SslHandler",
              false,
              HttpClientRequestTracingHandler.class.getClassLoader());
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private final ChannelHandlerContext ctx;

  public NettyRequestWrapper(HttpRequest request, ChannelHandlerContext ctx) {
    super(request);
    this.ctx = ctx;
  }

  @Override
  public boolean isHttps() {
    return sslHandlerClass != null && ctx.pipeline().get(sslHandlerClass) != null;
  }

  @Override
  public String getHostHeader() {
    return request.headers().get(HOST);
  }

  @Override
  public HttpVersion protocolVersion() {
    return request.getProtocolVersion();
  }

  @Override
  public String uri() {
    return request.getUri();
  }

  @Override
  public HttpMethod method() {
    return request().getMethod();
  }
}
