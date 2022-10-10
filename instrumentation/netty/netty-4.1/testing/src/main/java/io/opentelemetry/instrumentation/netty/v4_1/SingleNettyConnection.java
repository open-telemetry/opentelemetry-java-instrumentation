/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/*
Netty does not actually support proper http pipelining and has no way to correlate incoming response
message with some sent request. This means that without some support from the higher level protocol
we cannot concurrently send several requests across the same channel. Thus doRequest method of this
class is synchronised. Yes, it seems kinda pointless, but at least we test that our instrumentation
does not wreak havoc on Netty channel.
 */
public class SingleNettyConnection implements SingleConnection {
  private final String host;
  private final int port;
  private final Consumer<Channel> channelConsumer;
  private final Channel channel;

  public SingleNettyConnection(
      Bootstrap bootstrap, String host, int port, Consumer<Channel> channelConsumer) {
    this.host = host;
    this.port = port;
    this.channelConsumer = channelConsumer;

    ChannelFuture channelFuture = bootstrap.connect(host, port);
    channelFuture.awaitUninterruptibly();
    if (!channelFuture.isSuccess()) {
      throw new IllegalStateException(channelFuture.cause());
    } else {
      channel = channelFuture.channel();
    }
  }

  @Override
  public synchronized int doRequest(String path, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channelConsumer.accept(channel);

    channel.pipeline().addLast(new ClientHandler(result));

    HttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, path, Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, host + ":" + port);
    headers.forEach((k, v) -> request.headers().set(k, v));

    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }
}
