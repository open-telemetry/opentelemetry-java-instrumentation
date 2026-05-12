/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private static final VirtualField<Channel, NettyServerRequestAndContext> requestAndContextField =
      VirtualField.find(Channel.class, NettyServerRequestAndContext.class);

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent msg) throws Exception {
    NettyServerRequestAndContext requestAndContext = requestAndContextField.get(ctx.getChannel());

    if (requestAndContext == null || !(msg.getMessage() instanceof HttpResponse)) {
      super.writeRequested(ctx, msg);
      return;
    }

    Context context = requestAndContext.context();
    NettyRequest request = requestAndContext.request();
    HttpResponse response = (HttpResponse) msg.getMessage();
    customizeResponse(context, response);

    requestAndContextField.set(ctx.getChannel(), null);
    try (Scope ignored = context.makeCurrent()) {
      super.writeRequested(ctx, msg);
    } catch (Throwable t) {
      end(context, request, response, t);
      throw t;
    }
    end(context, request, response, null);
  }

  private static void end(
      Context context, NettyRequest request, HttpResponse response, @Nullable Throwable error) {
    instrumenter().end(context, request, response, NettyErrorHolder.getOrDefault(context, error));
  }

  private static void customizeResponse(Context context, HttpResponse response) {
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, response, NettyHttpResponseMutator.INSTANCE);
  }
}
