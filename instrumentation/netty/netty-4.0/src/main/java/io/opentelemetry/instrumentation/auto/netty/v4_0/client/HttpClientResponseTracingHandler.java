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

package io.opentelemetry.instrumentation.auto.netty.v4_0.client;

import static io.opentelemetry.instrumentation.auto.netty.v4_0.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.netty.v4_0.AttributeKeys;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Attribute<Span> parentAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(DefaultSpan.getInvalid());
    Span parent = parentAttr.get();
    Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (Scope scope = currentContextWith(span)) {
        TRACER.end(span, (HttpResponse) msg);
      }
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope scope = currentContextWith(parent)) {
      ctx.fireChannelRead(msg);
    }
  }
}
