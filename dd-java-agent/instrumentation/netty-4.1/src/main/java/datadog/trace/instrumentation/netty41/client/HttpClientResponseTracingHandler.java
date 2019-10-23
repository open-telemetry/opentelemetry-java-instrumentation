package datadog.trace.instrumentation.netty41.client;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.noopSpan;
import static datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.netty41.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;

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
      scope.setAsyncPropagation(true);
      ctx.fireChannelRead(msg);
    }
  }
}
