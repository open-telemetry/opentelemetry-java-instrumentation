/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Netty40ClientTest extends AbstractHttpClientTest<Request> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

  Bootstrap bootstrap = buildBootstrap(false);

  Bootstrap readTimeoutBootstrap = buildBootstrap(true);

  @AfterEach
  public void cleanupSpec() {
    if (eventLoopGroup != null) {
      eventLoopGroup.shutdownGracefully();
    }
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
  public Request buildRequest(String method, URI uri, Map<String, String> headers) {
    String target = uri.getPath();
    if (uri.getQuery() != null) {
      target += "?" + uri.getQuery();
    }
    DefaultFullHttpRequest defaultFullHttpRequest =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), target, Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(defaultFullHttpRequest, uri.getHost() + ":" + uri.getPort());
    defaultFullHttpRequest.headers().set("user-agent", "Netty");
    RequestBuilder requestBuilder = new RequestBuilder(method).setUrl(uri.toString());
    headers.forEach(requestBuilder::addHeader);
    return requestBuilder.build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    Channel channel = getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {
    Channel ch;
    try {
      ch = getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    } catch (Exception exception) {
      httpClientResult.complete(exception);
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
    optionsBuilder.disableTestReadTimeout();

    optionsBuilder.setExpectedClientSpanNameMapper(this::expectedClientSpanName);
    optionsBuilder.setHttpAttributes(this::httpAttributes);
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

  private String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }

  private Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return Collections.emptySet();
    }
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(SemanticAttributes.SERVER_ADDRESS);
    attributes.remove(SemanticAttributes.SERVER_PORT);
    return attributes;
  }
}
