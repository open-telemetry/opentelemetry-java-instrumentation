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

package io.opentelemetry.auto.instrumentation.netty.v4_1.server;

import static io.opentelemetry.auto.instrumentation.netty.v4_1.server.NettyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    Context context = TRACER.getServerContext(ctx.channel());
    if (context == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    Span span = TracingContextUtils.getSpan(context);
    try (final Scope ignored = ContextUtils.withScopedContext(context)) {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
    TRACER.end(span, ((HttpResponse) msg).status().code());
  }
}
