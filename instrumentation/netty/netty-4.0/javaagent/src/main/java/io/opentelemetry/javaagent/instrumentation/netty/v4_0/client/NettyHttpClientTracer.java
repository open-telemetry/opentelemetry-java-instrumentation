/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.opentelemetry.api.trace.Span.Kind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyResponseInjectAdapter.SETTER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyHttpClientTracer extends HttpClientTracer<HttpRequest, HttpResponse> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public Context startOperation(Context parentContext, ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      return noopContext(parentContext);
    }
    if (inClientSpan(parentContext)) {
      return noopContext(parentContext);
    }

    HttpRequest request = (HttpRequest) msg;

    SpanBuilder spanBuilder =
        tracer.spanBuilder(spanName(request)).setSpanKind(CLIENT).setParent(parentContext);
    onRequest(spanBuilder, request);
    NetPeerUtils.INSTANCE.setNetPeer(
        spanBuilder::setAttribute, (InetSocketAddress) ctx.channel().remoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .inject(context, request.headers(), SETTER);
    return context;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.getProtocolVersion().text();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().get(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.netty";
  }
}
