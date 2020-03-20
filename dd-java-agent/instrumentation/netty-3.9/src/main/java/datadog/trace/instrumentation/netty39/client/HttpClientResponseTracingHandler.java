package datadog.trace.instrumentation.netty39.client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.noopSpan;
import static datadog.trace.instrumentation.netty39.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.netty39.ChannelState;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelState> contextStore;

  public HttpClientResponseTracingHandler(final ContextStore<Channel, ChannelState> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent msg)
      throws Exception {
    final ChannelState channelState =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelState.Factory.INSTANCE);

    AgentSpan parent = channelState.getClientParentSpan();
    if (parent == null) {
      parent = noopSpan();
      channelState.setClientParentSpan(noopSpan());
    }
    final AgentSpan span = channelState.getClientSpan();

    final boolean finishSpan = msg.getMessage() instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (final AgentScope scope = activateSpan(span, false)) {
        DECORATE.onResponse(span, (HttpResponse) msg.getMessage());
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }

    // We want the callback in the scope of the parent, not the client span
    try (final AgentScope scope = activateSpan(parent, false)) {
      scope.setAsyncPropagation(true);
      ctx.sendUpstream(msg);
    }
  }
}
