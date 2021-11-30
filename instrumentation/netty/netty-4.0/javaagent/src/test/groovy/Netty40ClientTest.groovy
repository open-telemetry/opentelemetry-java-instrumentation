/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.timeout.ReadTimeoutHandler
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class Netty40ClientTest extends HttpClientTest<DefaultFullHttpRequest> implements AgentTestTrait {

  @Shared
  private EventLoopGroup eventLoopGroup = new NioEventLoopGroup()

  @Shared
  private Bootstrap bootstrap = buildBootstrap()

  @Shared
  private Bootstrap readTimeoutBootstrap = buildBootstrap(true)

  def cleanupSpec() {
    eventLoopGroup?.shutdownGracefully()
  }

  Bootstrap buildBootstrap(boolean readTimeout = false) {
    Bootstrap bootstrap = new Bootstrap()
    bootstrap.group(eventLoopGroup)
      .channel(NioSocketChannel)
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()
          if (readTimeout) {
            pipeline.addLast(new ReadTimeoutHandler(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          }
          pipeline.addLast(new HttpClientCodec())
        }
      })

    return bootstrap
  }

  Bootstrap getBootstrap(URI uri) {
    if (uri.getPath() == "/read-timeout") {
      return readTimeoutBootstrap
    }
    return bootstrap
  }

  @Override
  DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri.toString(), Unpooled.EMPTY_BUFFER)
    HttpHeaders.setHost(request, uri.host)
    request.headers().set("user-agent", userAgent())
    headers.each { k, v -> request.headers().set(k, v) }
    return request
  }

  @Override
  int sendRequest(DefaultFullHttpRequest request, String method, URI uri, Map<String, String> headers) {
    def channel = getBootstrap(uri).connect(uri.host, getPort(uri)).sync().channel()
    def result = new CompletableFuture<Integer>()
    channel.pipeline().addLast(new ClientHandler(result))
    channel.writeAndFlush(request).get()
    return result.get(20, TimeUnit.SECONDS)
  }

  @Override
  void sendRequestWithCallback(DefaultFullHttpRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    Channel ch
    try {
      ch = getBootstrap(uri).connect(uri.host, getPort(uri)).sync().channel()
    } catch (Exception exception) {
      requestResult.complete(exception)
      return
    }
    def result = new CompletableFuture<Integer>()
    result.whenComplete { status, throwable ->
      requestResult.complete({ status }, throwable)
    }
    ch.pipeline().addLast(new ClientHandler(result))
    ch.writeAndFlush(request)
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
  }

  @Override
  String userAgent() {
    return "Netty"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testHttps() {
    false
  }

  @Override
  boolean testReadTimeout() {
    true
  }
}
