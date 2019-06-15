package datadog.trace.instrumentation.netty41.client;

import static datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Span parent = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).get();
    final Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    final boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
        DECORATE.onResponse(span, (HttpResponse) msg);
        DECORATE.beforeFinish(span);
      }
    }

    // We want the callback in the scope of the parent, not the client span
    if (parent != null) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(parent, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }
}
