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

package io.opentelemetry.instrumentation.auto.netty.v3_8.server;

import static io.opentelemetry.instrumentation.auto.netty.v3_8.server.NettyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.trace.Span;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpServerRequestTracingHandler(
      ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    if (!(msg.getMessage() instanceof HttpRequest)) {
      Context serverSpanContext = TRACER.getServerContext(channelTraceContext);
      if (serverSpanContext == null) {
        ctx.sendUpstream(msg);
      } else {
        try (Scope ignored = ContextUtils.withScopedContext(serverSpanContext)) {
          ctx.sendUpstream(msg);
        }
      }
      return;
    }

    HttpRequest request = (HttpRequest) msg.getMessage();

    Span span = TRACER.startSpan(request, ctx.getChannel(), "netty.request");
    try (Scope ignored = TRACER.startScope(span, channelTraceContext)) {
      ctx.sendUpstream(msg);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
  }
}
