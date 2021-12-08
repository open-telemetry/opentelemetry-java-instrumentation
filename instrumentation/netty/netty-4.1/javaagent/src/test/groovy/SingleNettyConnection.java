/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
  private final Channel channel;

  public SingleNettyConnection(String host, int port) {
    this.host = host;
    this.port = port;
    EventLoopGroup group = new NioEventLoopGroup();
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new HttpClientCodec());
              }
            });

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
