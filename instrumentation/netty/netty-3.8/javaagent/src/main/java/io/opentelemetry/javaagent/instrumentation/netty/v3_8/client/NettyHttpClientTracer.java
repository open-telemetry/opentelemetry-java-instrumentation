/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyResponseInjectAdapter.SETTER;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class NettyHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  private NettyHttpClientTracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, ChannelHandlerContext ctx, HttpRequest request) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, spanNameForRequest(request), CLIENT);
    onRequest(spanBuilder, request);
    NetPeerAttributes.INSTANCE.setNetPeer(
        spanBuilder, (InetSocketAddress) ctx.getChannel().getRemoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    inject(context, request.headers(), SETTER);
    return context;
  }

  public void connectionFailure(Context parentContext, Channel channel, Throwable throwable) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, "CONNECT", CLIENT);
    spanBuilder.setAttribute(
        SemanticAttributes.NET_TRANSPORT, channel instanceof DatagramChannel ? IP_UDP : IP_TCP);
    NetPeerAttributes.INSTANCE.setNetPeer(
        spanBuilder, (InetSocketAddress) channel.getRemoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    tracer().endExceptionally(context, throwable);
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  @Nullable
  protected String flavor(HttpRequest httpRequest) {
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
  protected TextMapSetter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.netty-3.8";
  }
}
