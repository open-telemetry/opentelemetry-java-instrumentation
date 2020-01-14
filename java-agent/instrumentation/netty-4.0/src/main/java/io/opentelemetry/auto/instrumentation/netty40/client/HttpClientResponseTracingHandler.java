package io.opentelemetry.auto.instrumentation.netty40.client;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.noopSpan;
import static io.opentelemetry.auto.instrumentation.netty40.client.NettyHttpClientDecorator.DECORATE;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.netty40.AttributeKeys;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Attribute<AgentSpan> parentAttr =
        ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(noopSpan());
    final AgentSpan parent = parentAttr.get();
    final AgentSpan span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    final boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (final AgentScope scope = activateSpan(span, false)) {
        DECORATE.onResponse(span, (HttpResponse) msg);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }

    // We want the callback in the scope of the parent, not the client span
    try (final AgentScope scope = activateSpan(parent, false)) {
      ctx.fireChannelRead(msg);
    }
  }
}
