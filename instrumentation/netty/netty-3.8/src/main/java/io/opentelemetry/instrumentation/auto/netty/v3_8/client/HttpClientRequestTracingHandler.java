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

package io.opentelemetry.instrumentation.auto.netty.v3_8.client;

import static io.opentelemetry.instrumentation.auto.netty.v3_8.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.instrumentation.auto.netty.v3_8.client.NettyResponseInjectAdapter.SETTER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.auto.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.decorator.BaseTracer;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientRequestTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent msg) {
    if (!(msg.getMessage() instanceof HttpRequest)) {
      ctx.sendDownstream(msg);
      return;
    }

    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    Scope parentScope = null;
    Span continuation = channelTraceContext.getConnectionContinuation();
    if (continuation != null) {
      parentScope = currentContextWith(continuation);
      channelTraceContext.setConnectionContinuation(null);
    }
    channelTraceContext.setClientParentSpan(TRACER.getCurrentSpan());

    HttpRequest request = (HttpRequest) msg.getMessage();

    Span span = TRACER.startSpan(request);
    BaseTracer.onPeerConnection(span, (InetSocketAddress) ctx.getChannel().getRemoteAddress());
    Context context = withSpan(span, Context.current());
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request.headers(), SETTER);

    channelTraceContext.setClientSpan(span);

    try (Scope scope = currentContextWith(span)) {
      ctx.sendDownstream(msg);
    } catch (final Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    } finally {
      if (parentScope != null) {
        parentScope.close();
      }
    }
  }
}
