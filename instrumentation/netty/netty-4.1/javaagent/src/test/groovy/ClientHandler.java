/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import java.util.concurrent.CompletableFuture;

/*
Bridges from async Netty world to the sync world of our http client tests.
When request initiated by a test gets a response, calls a given callback and completes given
future with response's status code.
*/
public class ClientHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(ClientHandler.class, "http-response");

  private final CompletableFuture<Integer> responseCode;

  public ClientHandler(CompletableFuture<Integer> responseCode) {
    this.responseCode = responseCode;
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (msg instanceof FullHttpResponse) {
      ctx.pipeline().remove(this);
      FullHttpResponse response = (FullHttpResponse) msg;
      responseCode.complete(response.getStatus().code());
    } else if (msg instanceof HttpResponse) {
      // Headers before body have been received, store them to use when finishing the span.
      ctx.channel().attr(HTTP_RESPONSE).set((HttpResponse) msg);
    } else if (msg instanceof LastHttpContent) {
      ctx.pipeline().remove(this);
      responseCode.complete(ctx.channel().attr(HTTP_RESPONSE).get().getStatus().code());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    responseCode.completeExceptionally(cause);
    ctx.close();
  }
}
