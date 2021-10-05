/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.instrumentation.netty.common.server.NettyRequestExtractAdapter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, Channel, Channel> {
  private static final NettyHttpServerTracer TRACER = new NettyHttpServerTracer();

  public static NettyHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.status().code();
  }

  @Override
  protected void attachServerContext(Context context, Channel channel) {
    channel.attr(AttributeKeys.SERVER_CONTEXT).set(context);
  }

  @Override
  public Context getServerContext(Channel channel) {
    return channel.attr(AttributeKeys.SERVER_CONTEXT).get();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.netty-4.1";
  }

  @Override
  protected TextMapGetter<HttpRequest> getGetter() {
    return NettyRequestExtractAdapter.GETTER;
  }

  @Override
  protected String url(HttpRequest request) {
    String uri = request.uri();
    if (isRelativeUrl(uri) && request.headers().contains(HOST)) {
      return "http://" + request.headers().get(HOST) + request.uri();
    } else {
      return uri;
    }
  }

  @Override
  @Nullable
  protected String scheme(HttpRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected String host(HttpRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected String target(HttpRequest request) {
    return null;
  }

  @Override
  protected String peerHostIp(Channel channel) {
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected String flavor(Channel channel, HttpRequest request) {
    return request.protocolVersion().toString();
  }

  @Override
  protected Integer peerPort(Channel channel) {
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }
}
