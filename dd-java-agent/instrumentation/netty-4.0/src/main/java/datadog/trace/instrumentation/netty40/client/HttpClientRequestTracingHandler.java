package datadog.trace.instrumentation.netty40.client;

import static datadog.trace.instrumentation.netty40.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    TraceScope parentScope = null;
    final TraceScope.Continuation continuation =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY).getAndRemove();
    if (continuation != null) {
      parentScope = continuation.activate();
    }

    final HttpRequest request = (HttpRequest) msg;

    final Span span = GlobalTracer.get().buildSpan("netty.client.request").start();
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      DECORATE.onPeerConnection(span, (InetSocketAddress) ctx.channel().remoteAddress());

      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        GlobalTracer.get()
            .inject(
                span.context(),
                Format.Builtin.HTTP_HEADERS,
                new NettyResponseInjectAdapter(request));
      }

      ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

      try {
        ctx.write(msg, prm);
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
