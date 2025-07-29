/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/*
Bridges from async Netty world to the sync world of our http client tests.
When request initiated by a test gets a response, calls a given callback and completes given
future with response's status code.
*/
class ClientHandler extends ChannelInboundHandlerAdapter {
  private final CompletableFuture<Integer> result;

  public ClientHandler(CompletableFuture<Integer> result) {
    this.result = result;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) msg;
      result.complete(response.getStatus().code());
    }
    ctx.fireChannelRead(msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    result.completeExceptionally(cause);
    ctx.close();
  }
}
