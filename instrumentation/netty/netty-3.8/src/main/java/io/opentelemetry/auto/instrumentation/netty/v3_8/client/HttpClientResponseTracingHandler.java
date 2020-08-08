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

package io.opentelemetry.auto.instrumentation.netty.v3_8.client;

import static io.opentelemetry.auto.instrumentation.netty.v3_8.client.NettyHttpClientTracer.TRACER;

import io.opentelemetry.auto.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientResponseTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    Span parent = channelTraceContext.getClientParentSpan();
    if (parent == null) {
      parent = DefaultSpan.getInvalid();
      channelTraceContext.setClientParentSpan(DefaultSpan.getInvalid());
    }
    Span span = channelTraceContext.getClientSpan();

    boolean finishSpan = msg.getMessage() instanceof HttpResponse;

    if (span != null && finishSpan) {
      TRACER.end(span, (HttpResponse) msg.getMessage());
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope scope = TracingContextUtils.currentContextWith(parent)) {
      ctx.sendUpstream(msg);
    }
  }
}
