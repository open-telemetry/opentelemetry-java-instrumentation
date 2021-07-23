/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyResponseInjectAdapter.SETTER;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.AbstractNettyHttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyHttpClientTracerAccess;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyHttpClientTracer extends AbstractNettyHttpClientTracer<NettyRequestWrapper> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  static {
    NettyHttpClientTracerAccess.setTracer(TRACER);
  }

  private NettyHttpClientTracer() {}

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(
      Context parentContext, ChannelHandlerContext ctx, NettyRequestWrapper request) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, spanNameForRequest(request), CLIENT);
    onRequest(spanBuilder, request);
    NetPeerAttributes.INSTANCE.setNetPeer(
        spanBuilder, (InetSocketAddress) ctx.channel().remoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    inject(context, request.headers(), SETTER);
    return context;
  }

  @Override
  protected Context getAndRemoveConnectContext(ChannelFuture channelFuture) {
    return channelFuture.channel().attr(AttributeKeys.CONNECT_CONTEXT).getAndRemove();
  }

  @Override
  protected String method(NettyRequestWrapper httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  @Nullable
  protected String flavor(NettyRequestWrapper httpRequest) {
    return httpRequest.protocolVersion().text();
  }

  @Override
  protected URI url(NettyRequestWrapper request) throws URISyntaxException {
    URI uri = new URI(request.uri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      String protocol = request.isHttps() ? "https://" : "http://";
      uri = new URI(protocol + request.headers().get(HOST) + request.uri());
    }
    return uri;
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }

  @Override
  protected String requestHeader(NettyRequestWrapper httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().get(name);
  }

  @Override
  protected TextMapSetter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.netty-4.0";
  }
}
