/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class AbstractNettyHttpClientTracer extends BaseTracer {

  private static final boolean alwaysCreateConnectSpan =
      Config.get().getBoolean("otel.instrumentation.netty.always-create-connect-span", false);

  public Context startConnectionSpan(Context parentContext, SocketAddress remoteAddress) {
    if (!alwaysCreateConnectSpan) {
      return null;
    }

    SpanBuilder spanBuilder = spanBuilder(parentContext, "CONNECT", INTERNAL);
    NetPeerAttributes.INSTANCE.setNetPeer(spanBuilder, (InetSocketAddress) remoteAddress);

    return parentContext.with(spanBuilder.startSpan());
  }

  public void endConnectionSpan(
      Context context,
      Context parentContext,
      SocketAddress remoteAddress,
      Channel channel,
      Throwable throwable) {
    if (alwaysCreateConnectSpan) {
      if (context != null) {
        // if context is present we started span in startConnectionSpan
        endConnectionSpan(context, channel, throwable);
      }
    } else if (throwable != null && shouldStartSpan(parentContext, CLIENT)) {
      // if we didn't start span in startConnectionSpan create a span only when the request fails
      // and when not inside a client span
      connectionFailure(parentContext, remoteAddress, channel, throwable);
    }
  }

  private void endConnectionSpan(Context context, Channel channel, Throwable throwable) {
    if (channel != null) {
      Span span = Span.fromContext(context);
      span.setAttribute(
          SemanticAttributes.NET_TRANSPORT, channel instanceof DatagramChannel ? IP_UDP : IP_TCP);
      NetPeerAttributes.INSTANCE.setNetPeer(span, (InetSocketAddress) channel.remoteAddress());
    }
    if (throwable != null) {
      endExceptionally(context, throwable);
    } else {
      end(context);
    }
  }

  private void connectionFailure(
      Context parentContext, SocketAddress remoteAddress, Channel channel, Throwable throwable) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, "CONNECT", CLIENT);
    if (channel != null) {
      spanBuilder.setAttribute(
          SemanticAttributes.NET_TRANSPORT, channel instanceof DatagramChannel ? IP_UDP : IP_TCP);
      NetPeerAttributes.INSTANCE.setNetPeer(
          spanBuilder, (InetSocketAddress) channel.remoteAddress());
    } else if (remoteAddress != null) {
      NetPeerAttributes.INSTANCE.setNetPeer(spanBuilder, (InetSocketAddress) remoteAddress);
    }

    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    endExceptionally(context, throwable);
  }
}
