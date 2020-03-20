package datadog.trace.instrumentation.netty39.client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.netty39.client.NettyHttpClientDecorator.DECORATE;
import static datadog.trace.instrumentation.netty39.client.NettyResponseInjectAdapter.SETTER;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty39.ChannelState;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

@Slf4j
public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelState> contextStore;

  public HttpClientRequestTracingHandler(final ContextStore<Channel, ChannelState> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent msg)
      throws Exception {
    if (!(msg.getMessage() instanceof HttpRequest)) {
      ctx.sendDownstream(msg);
      return;
    }

    final ChannelState channelState =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelState.Factory.INSTANCE);

    TraceScope parentScope = null;
    final TraceScope.Continuation continuation = channelState.getConnectionContinuation();
    if (continuation != null) {
      parentScope = continuation.activate();
      channelState.setConnectionContinuation(null);
    }

    final HttpRequest request = (HttpRequest) msg.getMessage();

    channelState.setClientParentSpan(activeSpan());

    final AgentSpan span = startSpan("netty.client.request");
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      DECORATE.onPeerConnection(span, (InetSocketAddress) ctx.getChannel().getRemoteAddress());

      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        propagate().inject(span, request.headers(), SETTER);
      }

      channelState.setClientSpan(span);

      try {
        ctx.sendDownstream(msg);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
        throw throwable;
      }
    }

    if (null != parentScope) {
      parentScope.close();
    }
  }
}
