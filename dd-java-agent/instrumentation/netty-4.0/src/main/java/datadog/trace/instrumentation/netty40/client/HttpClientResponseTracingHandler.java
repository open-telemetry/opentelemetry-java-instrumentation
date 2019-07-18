package datadog.trace.instrumentation.netty40.client;

import static datadog.trace.instrumentation.netty40.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Attribute<Span> parentAttr =
        ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(NoopSpan.INSTANCE);
    final Span parent = parentAttr.get();
    final Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    final boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        DECORATE.onResponse(span, (HttpResponse) msg);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }

    // We want the callback in the scope of the parent, not the client span
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(parent, false)) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      ctx.fireChannelRead(msg);
    }
  }
}
