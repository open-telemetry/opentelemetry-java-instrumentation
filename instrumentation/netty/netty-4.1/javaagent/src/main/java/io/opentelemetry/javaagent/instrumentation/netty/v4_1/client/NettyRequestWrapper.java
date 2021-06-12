/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

public class NettyRequestWrapper {
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

  private final HttpRequest request;
  private final ChannelHandlerContext ctx;

  public NettyRequestWrapper(HttpRequest request, ChannelHandlerContext ctx) {
    this.request = request;
    this.ctx = ctx;
  }

  public HttpRequest request() {
    return request;
  }

  public boolean isHttps() {
    return sslHandlerClass != null && ctx.pipeline().get(sslHandlerClass) != null;
  }

  public HttpHeaders headers() {
    return request.headers();
  }

  public HttpVersion protocolVersion() {
    return request.protocolVersion();
  }

  public String uri() {
    return request.uri();
  }

  public HttpMethod method() {
    return request().method();
  }
}
