/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyResponseInjectAdapter.SETTER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.DefaultHttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public HttpClientOperation<HttpResponse> startOperation(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      return HttpClientOperation.noop();
    }

    Context parentContext = ctx.channel().attr(AttributeKeys.CONNECT_CONTEXT).getAndRemove();
    if (parentContext == null) {
      parentContext = Context.current();
    }

    if (!tracer().shouldStartSpan(parentContext)) {
      return HttpClientOperation.noop();
    }

    HttpRequest request = (HttpRequest) msg;
    Context context = startSpan(parentContext, request, request.headers(), -1);

    // TODO (trask) set this directly in span builder
    NetPeerUtils.INSTANCE.setNetPeer(
        Span.fromContext(context), (InetSocketAddress) ctx.channel().remoteAddress());

    return new DefaultHttpClientOperation<>(context, parentContext, this);
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.protocolVersion().text();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.uri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.uri());
    } else {
      return uri;
    }
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.status().code();
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
  protected Setter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.netty";
  }
}
