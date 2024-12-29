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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.netty.v4_1.ClientHandler;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty41ClientSslTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static HttpClientTestServer server;

  private static EventLoopGroup eventLoopGroup;

  private static Bootstrap createBootstrap(
      EventLoopGroup eventLoopGroup, List<String> enabledProtocols) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();

                SslContext sslContext = SslContextBuilder.forClient().build();
                SSLEngine sslEngine = sslContext.newEngine(socketChannel.alloc());
                if (enabledProtocols != null) {
                  sslEngine.setEnabledProtocols(enabledProtocols.toArray(new String[0]));
                }
                pipeline.addLast(new SslHandler(sslEngine));

                pipeline.addLast(new HttpClientCodec());
              }
            });
    return bootstrap;
  }

  @BeforeAll
  static void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    eventLoopGroup = new NioEventLoopGroup();
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
    server.stop().get(10, TimeUnit.SECONDS);
    eventLoopGroup.shutdownGracefully();
  }

  @Test
  @DisplayName("should fail SSL handshake")
  public void testFailSslHandshake() throws Exception {
    Bootstrap bootstrap = createBootstrap(eventLoopGroup, Collections.singletonList("SSLv3"));
    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + uri.getPort());

    Throwable finalThrownException =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      Channel channel =
                          bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
                      cleanup.deferCleanup(channel::close);
                      CompletableFuture<Integer> result = new CompletableFuture<>();
                      channel.pipeline().addLast(new ClientHandler(result));
                      channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
                      result.get(10, TimeUnit.SECONDS);
                    }));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(finalThrownException.getCause()),
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
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new SSLHandshakeException(null))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            satisfies(
                                SERVER_ADDRESS,
                                v ->
                                    v.satisfiesAnyOf(
                                        k -> assertThat(k).isNull(),
                                        k -> assertThat(k).isEqualTo(uri.getHost()))),
                            satisfies(
                                SERVER_PORT,
                                v ->
                                    v.satisfiesAnyOf(
                                        k -> assertThat(k).isNull(),
                                        k -> assertThat(k).isEqualTo(uri.getPort()))),
                            equalTo(NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"))));
  }

  @Test
  @DisplayName("should successfully establish SSL handshake")
  public void testSuccessSslHandshake() throws Exception {
    Bootstrap bootstrap = createBootstrap(eventLoopGroup, null);
    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + uri.getPort());

    testing.runWithSpan(
        "parent",
        () -> {
          Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
          cleanup.deferCleanup(channel::close);
          CompletableFuture<Integer> result = new CompletableFuture<>();
          channel.pipeline().addLast(new ClientHandler(result));
          channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
          result.get(10, TimeUnit.SECONDS);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
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
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1")),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT),
                span -> span.hasName("test-http-server").hasKind(SpanKind.SERVER)));
  }
}
