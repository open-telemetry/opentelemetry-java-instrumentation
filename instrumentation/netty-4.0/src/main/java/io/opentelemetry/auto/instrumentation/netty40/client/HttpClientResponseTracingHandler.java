package io.opentelemetry.auto.instrumentation.netty40.client;

import static io.opentelemetry.auto.instrumentation.netty40.client.NettyHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.netty40.client.NettyHttpClientDecorator.TRACER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.auto.instrumentation.netty40.AttributeKeys;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Attribute<Span> parentAttr =
        ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(DefaultSpan.getInvalid());
    final Span parent = parentAttr.get();
    final Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    final boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (final Scope scope = TRACER.withSpan(span)) {
        DECORATE.onResponse(span, (HttpResponse) msg);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }

    // We want the callback in the scope of the parent, not the client span
    try (final Scope scope = TRACER.withSpan(parent)) {
      ctx.fireChannelRead(msg);
    }
  }
}
