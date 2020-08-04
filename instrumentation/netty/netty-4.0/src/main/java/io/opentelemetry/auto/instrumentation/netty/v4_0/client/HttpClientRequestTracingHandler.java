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

package io.opentelemetry.auto.instrumentation.netty.v4_0.client;

import static io.opentelemetry.auto.instrumentation.netty.v4_0.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.auto.instrumentation.netty.v4_0.AttributeKeys;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    Scope parentScope = null;
    Span parentSpan =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_SPAN_ATTRIBUTE_KEY).getAndRemove();
    if (parentSpan != null) {
      parentScope = currentContextWith(parentSpan);
    }

    HttpRequest request = (HttpRequest) msg;

    Span currentSpan = TRACER.getCurrentSpan();
    if (currentSpan.getContext().isValid()) {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(currentSpan);
    } else {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(null);
    }

    Scope scope = null;
    try {
      Span span = TRACER.startSpan(request);
      TRACER.onPeerConnection(span, (InetSocketAddress) ctx.channel().remoteAddress());

      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        scope = TRACER.startScope(span, request);
      }

      ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        TRACER.endExceptionally(span, throwable);
        throw throwable;
      }
    } finally {
      if (null != parentScope) {
        parentScope.close();
      }

      if (null != scope) {
        scope.close();
      }
    }
  }
}
