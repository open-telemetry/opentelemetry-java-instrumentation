/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
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
import io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ConnectionSpanTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private HttpClientTestServer server;
  private EventLoopGroup eventLoopGroup;
  private final Bootstrap bootstrap = buildBootstrap();

  @BeforeEach
  void setupSpec() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    eventLoopGroup = new NioEventLoopGroup();
  }

  @AfterEach
  void cleanupSpec() {
    eventLoopGroup.shutdownGracefully();
    try {
      server.stop().get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Bootstrap buildBootstrap() {
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
  public void successfulRequest() throws Exception {
    // when
    URI uri = URI.create("http://localhost:${server.httpPort()}/success");

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
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(SemanticAttributes.SERVER_ADDRESS, uri.getHost()),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(2))));
  }

  @Test
  public void failedRequest() throws Exception {
    // when
    URI uri = URI.create("http://localhost:${PortUtils.UNUSABLE_PORT}");

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
                  span.hasAttributesSatisfyingExactly(
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(SemanticAttributes.SERVER_ADDRESS, uri.getHost()),
                      equalTo(SemanticAttributes.SERVER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
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

  private int sendRequest(DefaultFullHttpRequest request, URI uri)
      throws InterruptedException, ExecutionException, TimeoutException {
    Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
    CompletableFuture<Integer> result = new CompletableFuture<Integer>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(request).get();
    return result.get(20, TimeUnit.SECONDS);
  }
}
