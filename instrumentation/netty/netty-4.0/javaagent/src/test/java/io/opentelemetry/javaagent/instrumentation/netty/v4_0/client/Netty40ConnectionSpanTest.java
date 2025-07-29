/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
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
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ConnectionSpanTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static HttpClientTestServer server;
  private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
  private static final Bootstrap bootstrap = buildBootstrap();

  @BeforeAll
  static void setupSpec() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
  }

  @AfterAll
  static void cleanupSpec() throws InterruptedException, ExecutionException, TimeoutException {
    eventLoopGroup.shutdownGracefully();
    server.stop().get(10, TimeUnit.SECONDS);
  }

  static Bootstrap buildBootstrap() {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new HttpClientCodec());
              }
            });

    return bootstrap;
  }

  @Test
  void successfulRequest() throws Exception {
    // when
    URI uri = URI.create("http://localhost:" + server.httpPort() + "/success");

    DefaultFullHttpRequest request = buildRequest("GET", uri, new HashMap<>());
    int responseCode = testing.runWithSpan("parent", () -> sendRequest(request, uri));

    // then
    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> {
                  span.hasName("CONNECT").hasKind(INTERNAL).hasParent(trace.getSpan(0));
                  span.hasAttributesSatisfyingExactly(
                      equalTo(NETWORK_TRANSPORT, "tcp"),
                      equalTo(NETWORK_TYPE, "ipv4"),
                      equalTo(SERVER_ADDRESS, uri.getHost()),
                      equalTo(SERVER_PORT, uri.getPort()),
                      equalTo(NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(2))));
  }

  @Test
  void failedRequest() throws Exception {
    // when
    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT);

    DefaultFullHttpRequest request = buildRequest("GET", uri, new HashMap<>());
    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> sendRequest(request, uri)));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent().hasException(thrown),
                span -> {
                  span.hasName("CONNECT").hasKind(INTERNAL).hasParent(trace.getSpan(0));
                  span.hasAttributesSatisfying(equalTo(NETWORK_TRANSPORT, "tcp"));
                  satisfies(
                      NETWORK_TYPE,
                      val ->
                          val.satisfiesAnyOf(
                              v -> assertThat(val).isNull(), v -> assertThat(v).isEqualTo("ipv4")));
                  span.hasAttributesSatisfying(
                      equalTo(SERVER_ADDRESS, uri.getHost()), equalTo(SERVER_PORT, uri.getPort()));
                  satisfies(
                      NETWORK_PEER_PORT,
                      val ->
                          val.satisfiesAnyOf(
                              v -> assertThat(val).isNull(),
                              v -> assertThat(v).isEqualTo(uri.getPort())));
                  satisfies(
                      NETWORK_PEER_ADDRESS,
                      val ->
                          val.satisfiesAnyOf(
                              v -> assertThat(val).isNull(),
                              v -> assertThat(v).isEqualTo("127.0.0.1")));
                }));
  }

  private static DefaultFullHttpRequest buildRequest(
      String method, URI uri, Map<String, String> headers) {
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri.getPath(), Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());
    headers.forEach((k, v) -> request.headers().set(k, v));
    return request;
  }

  private static int sendRequest(DefaultFullHttpRequest request, URI uri)
      throws InterruptedException, ExecutionException, TimeoutException {
    Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
    CompletableFuture<Integer> result = new CompletableFuture<Integer>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }
}
