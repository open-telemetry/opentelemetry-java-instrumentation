/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientRequestAndContexts.RequestAndContexts;
import java.nio.channels.ClosedChannelException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) throws Exception {
    NettyClientRequestAndContexts requests = NettyClientRequestAndContexts.get(ctx.getChannel());

    if (requests == null) {
      super.messageReceived(ctx, msg);
      return;
    }

    Object message = msg.getMessage();
    RequestAndContexts request;
    if (message instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) message;
      int status = response.getStatus().getCode();
      if (status >= 100 && status < 200 && status != 101) {
        request = requests.peek();
      } else {
        request = requests.poll(response.isChunked());
        if (request != null) {
          request.end(response, null);
        }
      }
    } else if (message instanceof HttpChunk) {
      request = requests.response(((HttpChunk) message).isLast());
      if (request == null) {
        request = requests.peek();
      }
    } else {
      request = requests.peek();
    }
    if (request == null) {
      super.messageReceived(ctx, msg);
      return;
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = request.parentContext.makeCurrent()) {
      super.messageReceived(ctx, msg);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event) throws Exception {
    NettyClientRequestAndContexts requests = NettyClientRequestAndContexts.get(ctx.getChannel());
    if (requests != null) {
      requests.fail(event.getCause());
    }
    super.exceptionCaught(ctx, event);
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent event)
      throws Exception {
    NettyClientRequestAndContexts requests = NettyClientRequestAndContexts.get(ctx.getChannel());
    if (requests != null) {
      requests.fail(new ClosedChannelException());
    }
    super.channelDisconnected(ctx, event);
  }

  @Override
  public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent event) throws Exception {
    NettyClientRequestAndContexts requests = NettyClientRequestAndContexts.get(ctx.getChannel());
    if (requests != null) {
      requests.fail(new ClosedChannelException());
    }
    super.channelClosed(ctx, event);
  }
}
