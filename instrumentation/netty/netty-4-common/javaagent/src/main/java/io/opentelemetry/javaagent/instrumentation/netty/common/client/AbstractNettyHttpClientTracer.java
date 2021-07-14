/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;

public abstract class AbstractNettyHttpClientTracer<REQUEST>
    extends HttpClientTracer<REQUEST, HttpHeaders, HttpResponse> {

  protected AbstractNettyHttpClientTracer() {
    super(NetPeerAttributes.INSTANCE);
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
