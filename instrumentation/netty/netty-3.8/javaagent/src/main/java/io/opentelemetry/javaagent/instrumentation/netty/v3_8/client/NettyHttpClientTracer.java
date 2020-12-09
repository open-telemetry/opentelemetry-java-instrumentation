/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyResponseInjectAdapter.SETTER;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class NettyHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public HttpClientOperation<HttpResponse> startOperation(
      ChannelHandlerContext ctx, MessageEvent msg, ChannelTraceContext channelTraceContext) {

    if (!(msg.getMessage() instanceof HttpRequest)) {
      return HttpClientOperation.noop();
    }

    Context parentContext = channelTraceContext.getConnectionContext();
    if (parentContext != null) {
      channelTraceContext.setConnectionContext(null);
    } else {
      parentContext = Context.current();
    }

    if (inClientSpan(parentContext)) {
      return HttpClientOperation.noop();
    }

    HttpRequest request = (HttpRequest) msg.getMessage();

    SpanBuilder spanBuilder = spanBuilder(parentContext, request);
    NetPeerUtils.INSTANCE.setNetPeer(
        spanBuilder::setAttribute, (InetSocketAddress) ctx.getChannel().getRemoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    inject(request.headers(), context);
    return newOperation(context, parentContext);
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.getProtocolVersion().getText();
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
    return httpResponse.getStatus().getCode();
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
