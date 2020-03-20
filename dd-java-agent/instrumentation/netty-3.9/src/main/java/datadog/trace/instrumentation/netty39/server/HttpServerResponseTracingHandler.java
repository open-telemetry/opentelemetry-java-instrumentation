package datadog.trace.instrumentation.netty39.server;

import static datadog.trace.instrumentation.netty39.server.NettyHttpServerDecorator.DECORATE;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.instrumentation.netty39.ChannelState;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelState> contextStore;

  public HttpServerResponseTracingHandler(final ContextStore<Channel, ChannelState> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent msg)
      throws Exception {
    final ChannelState channelState =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelState.Factory.INSTANCE);

    final AgentSpan span = channelState.getServerSpan();
    if (span == null || !(msg.getMessage() instanceof HttpResponse)) {
      ctx.sendDownstream(msg);
      return;
    }

    final HttpResponse response = (HttpResponse) msg.getMessage();

    try {
      ctx.sendDownstream(msg);
    } catch (final Throwable throwable) {
      DECORATE.onError(span, throwable);
      span.setTag(Tags.HTTP_STATUS, 500);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }
    DECORATE.onResponse(span, response);
    DECORATE.beforeFinish(span);
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
