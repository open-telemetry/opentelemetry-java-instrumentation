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

package io.opentelemetry.auto.instrumentation.netty.v4_0.server;

import static io.opentelemetry.auto.instrumentation.netty.v4_0.server.NettyHttpServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    Channel channel = ctx.channel();

    if (!(msg instanceof HttpRequest)) {
      Span serverSpan = TRACER.getServerSpan(channel);
      if (serverSpan == null) {
        ctx.fireChannelRead(msg);
      } else {
        try (Scope ignored = currentContextWith(serverSpan)) {
          ctx.fireChannelRead(msg);
        }
      }
      return;
    }

    Span span = TRACER.startSpan((HttpRequest) msg, channel, "netty.request");
    try (Scope ignored = TRACER.startScope(span, channel)) {
      ctx.fireChannelRead(msg);
    } catch (final Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
  }
}
