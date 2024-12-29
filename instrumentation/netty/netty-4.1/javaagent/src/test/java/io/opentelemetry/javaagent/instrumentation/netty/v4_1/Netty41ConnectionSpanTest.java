/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.netty.v4_1.ClientHandler;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty41ConnectionSpanTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private HttpClientTestServer server;
  private EventLoopGroup eventLoopGroup;

  private Bootstrap bootstrap;

  @BeforeEach
  void setUp() {
    eventLoopGroup = new NioEventLoopGroup();
    bootstrap = buildBootstrap();
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
  }

  @AfterEach
  void tearDown() {
    eventLoopGroup.shutdownGracefully();
    server.stop();
  }

  Bootstrap buildBootstrap() {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new HttpClientCodec());
              }
            });

    return bootstrap;
  }

  DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri.getPath(), Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + uri.getPort());
    headers.forEach((k, v) -> request.headers().set(k, v));
    return request;
  }

  int sendRequest(DefaultFullHttpRequest request, URI uri)
      throws ExecutionException, InterruptedException, TimeoutException {
    Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("test successful request")
  void testSuccessfulRequest() throws Exception {
    URI uri = URI.create("http://localhost:" + server.httpPort() + "/success");
    DefaultFullHttpRequest request = buildRequest("GET", uri, new HashMap<>());
    int responseCode = testing.runWithSpan("parent", () -> sendRequest(request, uri));

    assertThat(responseCode).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1")),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(3))));
  }

  @Test
  @DisplayName("test failing request")
  void testFailingRequest() throws Exception {
    URI uri = URI.create("http://localhost:" + PortUtils.UNUSABLE_PORT);
    DefaultFullHttpRequest request = buildRequest("GET", uri, new HashMap<>());
    Throwable finalThrownException =
        catchThrowable(() -> testing.runWithSpan("parent", () -> sendRequest(request, uri)));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(finalThrownException),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(finalThrownException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            satisfies(
                                NETWORK_TYPE,
                                k ->
                                    k.satisfiesAnyOf(
                                        v -> assertThat(v).isEqualTo("ipv4"),
                                        v -> assertThat(v).isNull())),
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort()),
                            satisfies(
                                NETWORK_PEER_PORT,
                                k ->
                                    k.satisfiesAnyOf(
                                        v -> assertThat(v).isEqualTo(uri.getPort()),
                                        v -> assertThat(v).isNull())),
                            satisfies(
                                NETWORK_PEER_ADDRESS,
                                k ->
                                    k.satisfiesAnyOf(
                                        v -> assertThat(v).isEqualTo("127.0.0.1"),
                                        v -> assertThat(v).isNull())))));
  }
}
