/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.netty.v3_8.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.instrumentation.netty.v3_8.server.NettyHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.netty.v3_8.server.NettyHttpServerDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.netty.v3_8.server.NettyRequestExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpServerRequestTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent msg) {
    final ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    if (!(msg.getMessage() instanceof HttpRequest)) {
      final Span span = channelTraceContext.getServerSpan();
      if (span == null) {
        ctx.sendUpstream(msg); // superclass does not throw
      } else {
        try (final Scope scope = TRACER.withSpan(span)) {
          ctx.sendUpstream(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg.getMessage();

    final Span.Builder spanBuilder =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(SERVER);
    final SpanContext extractedContext = extract(request.headers(), GETTER);
    if (extractedContext.isValid()) {
      spanBuilder.setParent(extractedContext);
    } else {
      // explicitly setting "no parent" in case a span was propagated to this thread
      // by the java-concurrent instrumentation when the thread was started
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, ctx.getChannel());
      DECORATE.onRequest(span, request);

      channelTraceContext.setServerSpan(span);

      try {
        ctx.sendUpstream(msg);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end(); // Finish the span manually since finishSpanOnClose was false
        throw throwable;
      }
    }
  }
}
