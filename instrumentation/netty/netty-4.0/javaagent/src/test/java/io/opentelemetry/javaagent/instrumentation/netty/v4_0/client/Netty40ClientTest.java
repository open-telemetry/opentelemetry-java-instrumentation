/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ClientTest extends AbstractHttpClientTest<DefaultFullHttpRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
  private final Bootstrap bootstrap = buildBootstrap(false);
  private final Bootstrap readTimeoutBootstrap = buildBootstrap(true);

  @AfterAll
  void cleanup() {
    eventLoopGroup.shutdownGracefully();
  }

  Bootstrap buildBootstrap(boolean readTimeout) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                if (readTimeout) {
                  pipeline.addLast(new ReadTimeoutHandler(2000, TimeUnit.MILLISECONDS));
                }
                pipeline.addLast(new HttpClientCodec());
              }
            });

    return bootstrap;
  }

  private Bootstrap getBootstrap(URI uri) {
    if ("/read-timeout".equals(uri.getPath())) {
      return readTimeoutBootstrap;
    }
    return bootstrap;
  }

  @Override
  public DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    String target = uri.getPath();
    if (uri.getQuery() != null) {
      target += "?" + uri.getQuery();
    }
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), target, Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());
    request.headers().set("user-agent", "Netty");
    headers.forEach((k, v) -> request.headers().set(k, v));
    return request;
  }

  @Override
  public int sendRequest(
      DefaultFullHttpRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    Channel channel = getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }

  @Override
  public void sendRequestWithCallback(
      DefaultFullHttpRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {
    Channel ch;
    try {
      ch = getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    } catch (Throwable th) {
      httpClientResult.complete(th);
      return;
    }
    CompletableFuture<Integer> result = new CompletableFuture<>();
    result.whenComplete((status, throwable) -> httpClientResult.complete(() -> status, throwable));
    ch.pipeline().addLast(new ClientHandler(result));
    ch.writeAndFlush(request);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.disableTestHttps();
    optionsBuilder.spanEndsAfterBody();

    optionsBuilder.setExpectedClientSpanNameMapper(Netty40ClientTest::expectedClientSpanName);
    optionsBuilder.setHttpAttributes(Netty40ClientTest::httpAttributes);
  }

  private static int getPort(URI uri) {
    int port = uri.getPort();
    if (port == -1) {
      switch (uri.getScheme()) {
        case "http":
          return 80;
        case "https":
          return 443;
        default:
          throw new IllegalArgumentException("Unknown scheme: " + uri.getScheme());
      }
    }
    return port;
  }

  private static String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }

  @SuppressWarnings("MissingDefault")
  private static Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return Collections.emptySet();
    }
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(SERVER_ADDRESS);
    attributes.remove(SERVER_PORT);
    return attributes;
  }
}
