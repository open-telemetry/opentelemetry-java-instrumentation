package io.opentelemetry.auto.instrumentation.netty41.server;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.netty41.server.NettyHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.netty41.server.NettyRequestExtractAdapter.GETTER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.AgentSpan.Context;
import io.opentelemetry.auto.instrumentation.netty41.AttributeKeys;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

    if (!(msg instanceof HttpRequest)) {
      final AgentSpan span = ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
      if (span == null) {
        ctx.fireChannelRead(msg); // superclass does not throw
      } else {
        try (final AgentScope scope = activateSpan(span, false)) {
          ctx.fireChannelRead(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final Context extractedContext = propagate().extract(request.headers(), GETTER);

    final AgentSpan span = startSpan("netty.request", extractedContext);
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, ctx.channel());
      DECORATE.onRequest(span, request);

      ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).set(span);

      try {
        ctx.fireChannelRead(msg);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish(); // Finish the span manually since finishSpanOnClose was false
        throw throwable;
      }
    }
  }
}
