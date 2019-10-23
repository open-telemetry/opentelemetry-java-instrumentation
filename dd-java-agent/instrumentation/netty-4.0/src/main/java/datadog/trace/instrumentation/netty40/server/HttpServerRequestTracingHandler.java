package datadog.trace.instrumentation.netty40.server;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.netty40.server.NettyHttpServerDecorator.DECORATE;
import static datadog.trace.instrumentation.netty40.server.NettyRequestExtractAdapter.GETTER;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentSpan.Context;
import datadog.trace.instrumentation.netty40.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

    if (!(msg instanceof HttpRequest)) {
      final AgentSpan span = ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
      if (span == null) {
        ctx.fireChannelRead(msg); // superclass does not throw
      } else {
        try (final AgentScope scope = activateSpan(span, false)) {
          scope.setAsyncPropagation(true);
          ctx.fireChannelRead(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final Context context = propagate().extract(request.headers(), GETTER);

    final AgentSpan span = startSpan("netty.request", context);
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, ctx.channel());
      DECORATE.onRequest(span, request);

      scope.setAsyncPropagation(true);

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
