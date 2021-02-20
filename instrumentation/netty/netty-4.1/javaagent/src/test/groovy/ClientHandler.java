/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.lang.Closure;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/*
Bridges from async Netty world to the sync world of our http client tests.
When request initiated by a test gets a response, calls a given callback and completes given
future with response's status code.
*/
public class ClientHandler extends SimpleChannelInboundHandler<HttpObject> {
  private final Closure<Void> callback;
  private final CompletableFuture<Integer> responseCode;

  public ClientHandler(Closure<Void> callback, CompletableFuture<Integer> responseCode) {
    this.callback = callback;
    this.responseCode = responseCode;
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (msg instanceof HttpResponse) {
      ctx.pipeline().remove(this);

      if (callback != null) {
        callback.call();
      }

      HttpResponse response = (HttpResponse) msg;
      responseCode.complete(response.getStatus().code());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
