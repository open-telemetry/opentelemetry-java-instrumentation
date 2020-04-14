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

import static io.opentelemetry.auto.instrumentation.netty.v4_0.server.NettyHttpServerDecorator.DECORATE;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.instrumentation.netty.v4_0.AttributeKeys;
import io.opentelemetry.trace.Span;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final Span span = ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpResponse response = (HttpResponse) msg;

    try {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      DECORATE.onError(span, throwable);
      span.setAttribute(Tags.HTTP_STATUS, 500);
      span.end(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }
    DECORATE.onResponse(span, response);
    DECORATE.beforeFinish(span);
    span.end(); // Finish the span manually since finishSpanOnClose was false
  }
}
