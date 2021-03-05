/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class NettyHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, Channel, ChannelTraceContext> {
  private static final NettyHttpServerTracer TRACER = new NettyHttpServerTracer();

  public static NettyHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.getStatus().getCode();
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
  protected String url(HttpRequest request) {
    String uri = request.getUri();
    if (isRelativeUrl(uri) && request.headers().contains(HOST)) {
      return "http://" + request.headers().get(HOST) + request.getUri();
    } else {
      return uri;
    }
  }

  @Override
  protected String peerHostIP(Channel channel) {
    SocketAddress socketAddress = channel.getRemoteAddress();
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
  protected TextMapGetter<HttpRequest> getGetter() {
    return NettyRequestExtractAdapter.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.netty-3.8";
  }

  @Override
  protected Integer peerPort(Channel channel) {
    SocketAddress socketAddress = channel.getRemoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }
}
