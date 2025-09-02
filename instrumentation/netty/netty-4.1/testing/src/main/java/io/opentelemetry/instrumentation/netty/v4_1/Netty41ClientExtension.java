/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class Netty41ClientExtension implements BeforeAllCallback, AfterAllCallback {

  private final Consumer<ChannelPipeline> channelPipelineConfigurer;
  private final Supplier<EventLoopGroup> eventLoopGroupSupplier;
  private final Class<? extends Channel> channelClass;

  private EventLoopGroup eventLoopGroup;
  private Bootstrap httpBootstrap;
  private Bootstrap httpsBootstrap;
  private Bootstrap readTimeoutBootstrap;

  public Netty41ClientExtension(Consumer<ChannelPipeline> channelPipelineConfigurer) {
    this(channelPipelineConfigurer, NioEventLoopGroup::new, NioSocketChannel.class);
  }

  public Netty41ClientExtension(
      Consumer<ChannelPipeline> channelPipelineConfigurer,
      Supplier<EventLoopGroup> eventLoopGroupSupplier,
      Class<? extends Channel> channelClass) {
    this.channelPipelineConfigurer = channelPipelineConfigurer;
    this.eventLoopGroupSupplier = eventLoopGroupSupplier;
    this.channelClass = channelClass;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    eventLoopGroup = eventLoopGroupSupplier.get();
    httpBootstrap = buildBootstrap(eventLoopGroup, false, false);
    httpsBootstrap = buildBootstrap(eventLoopGroup, true, false);
    readTimeoutBootstrap = buildBootstrap(eventLoopGroup, false, true);
  }

  public Bootstrap buildBootstrap(boolean https, boolean readTimeout) {
    return buildBootstrap(eventLoopGroupSupplier.get(), https, readTimeout);
  }

  private Bootstrap buildBootstrap(
      EventLoopGroup eventLoopGroup, boolean https, boolean readTimeout) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(channelClass)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECTION_TIMEOUT.toMillis())
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                if (https) {
                  SslContext sslContext = SslContextBuilder.forClient().build();
                  pipeline.addLast(sslContext.newHandler(socketChannel.alloc(), "localhost", -1));
                }
                if (readTimeout) {
                  pipeline.addLast(
                      new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                }
                pipeline.addLast(new HttpClientCodec());
                channelPipelineConfigurer.accept(pipeline);
              }
            });
    return bootstrap;
  }

  @Override
  public void afterAll(ExtensionContext context) throws InterruptedException {
    if (eventLoopGroup != null) {
      eventLoopGroup.shutdownGracefully().await(10, TimeUnit.SECONDS);
    }
  }

  public Bootstrap getBootstrap(URI uri) {
    if ("https".equals(uri.getScheme())) {
      return httpsBootstrap;
    } else if ("/read-timeout".equals(uri.getPath())) {
      return readTimeoutBootstrap;
    }
    return httpBootstrap;
  }
}
