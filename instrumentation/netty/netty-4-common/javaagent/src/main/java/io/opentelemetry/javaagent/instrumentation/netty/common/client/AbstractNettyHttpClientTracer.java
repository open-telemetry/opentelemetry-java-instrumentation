/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyResponseInjectAdapter.SETTER;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.concurrent.Future;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class AbstractNettyHttpClientTracer<REQUEST extends AbstractNettyRequestWrapper>
    extends HttpClientTracer<REQUEST, HttpHeaders, HttpResponse> {

  protected AbstractNettyHttpClientTracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  public Context startSpan(Context parentContext, ChannelHandlerContext ctx, REQUEST request) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, spanNameForRequest(request), CLIENT);
    onRequest(spanBuilder, request);
    NetPeerAttributes.INSTANCE.setNetPeer(
        spanBuilder, (InetSocketAddress) ctx.channel().remoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    inject(context, request.headers(), SETTER);
    return context;
  }

  @Override
  protected String method(REQUEST httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  @Nullable
  protected String flavor(REQUEST httpRequest) {
    return httpRequest.protocolVersion().text();
  }

  @Override
  protected URI url(REQUEST request) throws URISyntaxException {
    URI uri = new URI(request.uri());
    String hostHeader = request.getHostHeader();
    if ((uri.getHost() == null || uri.getHost().equals("")) && hostHeader != null) {
      String protocol = request.isHttps() ? "https://" : "http://";
      uri = new URI(protocol + hostHeader + request.uri());
    }
    return uri;
  }

  @Override
  protected String requestHeader(AbstractNettyRequestWrapper httpRequest, String name) {
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

  public static void operationComplete(Future<?> future) {
    AbstractNettyHttpClientTracer tracer = NettyHttpClientTracerAccess.getTracer();
    if (tracer == null) {
      return;
    }

    if (!(future instanceof ChannelFuture)) {
      return;
    }
    // If first call to GenericFutureListener#operationComplete has an exception then we
    // treat it as the cause of connection failure and create a special span for it
    ChannelFuture channelFuture = (ChannelFuture) future;
    Context parentContext = tracer.getAndRemoveConnectContext(channelFuture);
    if (parentContext == null) {
      return;
    }
    Throwable cause = future.cause();
    if (cause == null) {
      return;
    }

    if (tracer.shouldStartSpan(parentContext, SpanKind.CLIENT)) {
      tracer.connectionFailure(parentContext, channelFuture.channel(), cause);
    }
  }

  protected abstract Context getAndRemoveConnectContext(ChannelFuture channelFuture);

  private void connectionFailure(Context parentContext, Channel channel, Throwable throwable) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, "CONNECT", CLIENT);
    spanBuilder.setAttribute(
        SemanticAttributes.NET_TRANSPORT, channel instanceof DatagramChannel ? IP_UDP : IP_TCP);
    NetPeerAttributes.INSTANCE.setNetPeer(spanBuilder, (InetSocketAddress) channel.remoteAddress());

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    endExceptionally(context, throwable);
  }
}
