/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyClientSingletons.HTTP_REQUEST;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyClientSingletons.instrumenter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    Context parentContext = ctx.channel().attr(AttributeKeys.WRITE_CONTEXT).getAndRemove();
    if (parentContext == null) {
      parentContext = Context.current();
    }

    HttpRequestAndChannel request = HttpRequestAndChannel.create((HttpRequest) msg, ctx.channel());
    if (!instrumenter().shouldStart(parentContext, request) || isAwsRequest(request)) {
      ctx.write(msg, prm);
      return;
    }

    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = ctx.channel().attr(HTTP_REQUEST);

    Context context = instrumenter().start(parentContext, request);
    parentContextAttr.set(parentContext);
    contextAttr.set(context);
    requestAttr.set(request);

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      instrumenter().end(contextAttr.getAndRemove(), requestAttr.getAndRemove(), null, throwable);
      parentContextAttr.remove();
      throw throwable;
    }
  }

  private static boolean isAwsRequest(HttpRequestAndChannel request) {
    // The AWS SDK uses Netty for asynchronous clients but constructs a request signature before
    // beginning transport. This means we MUST suppress Netty spans we would normally create or
    // they will inject their own trace header, which does not match what was present when the
    // signature was computed, breaking the SDK request completely. We have not found how to
    // cleanly propagate context from the SDK instrumentation, which executes on an application
    // thread, to Netty instrumentation, which executes on event loops. If it's possible, it may
    // require instrumenting internal classes. Using a header which is more or less guaranteed to
    // always exist is arguably more stable.
    return request.request().headers().contains("amz-sdk-invocation-id");
  }
}
