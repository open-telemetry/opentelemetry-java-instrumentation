/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.lang.Closure;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.CompletableFuture;

/*
Bridges from async Netty world to the sync world of our http client tests.
When request initiated by a test gets a response, calls a given callback and completes given
future with response's status code.
*/
public class ClientHandler extends SimpleChannelInboundHandler<HttpResponse> {
  private final Closure<Void> callback;
  private final CompletableFuture<Integer> responseCode;
  private final String requestId;

  public ClientHandler(Closure<Void> callback, CompletableFuture<Integer> responseCode) {
    this(callback, responseCode, null);
  }

  public ClientHandler(Closure<Void> callback, CompletableFuture<Integer> responseCode,
      String requestId) {
    this.callback = callback;
    this.responseCode = responseCode;
    this.requestId = requestId;
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpResponse response) {
    System.out.println("Waiting for response to " + requestId);
    if (requestId != null && !requestId.equals(response.headers().get("test-request-id"))) {
      ReferenceCountUtil.retain(response);
      ctx.fireChannelRead(response);
      return;
    }

    System.out.println("Got response for " + requestId);

    if (callback != null) {
      callback.call();
    }
    responseCode.complete(response.getStatus().code());
    ctx.pipeline().remove(this);

  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
