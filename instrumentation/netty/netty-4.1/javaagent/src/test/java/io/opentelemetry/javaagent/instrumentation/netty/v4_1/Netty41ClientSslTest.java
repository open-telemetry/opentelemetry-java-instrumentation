/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

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
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty41ClientSslTest {

  @RegisterExtension
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private HttpClientTestServer server;

  private EventLoopGroup eventLoopGroup;

  @SuppressWarnings("UnusedMethod")
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

  @BeforeEach
  void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    eventLoopGroup = new NioEventLoopGroup();
    System.setProperty("otel.instrumentation.netty.connection-telemetry.enabled","true");
    System.setProperty("otel.instrumentation.netty.ssl-telemetry.enabled","true");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
    server.stop().get(10, TimeUnit.SECONDS);
    eventLoopGroup.shutdownGracefully();
    System.clearProperty("otel.instrumentation.netty.connection-telemetry.enabled");
    System.clearProperty("otel.instrumentation.netty.ssl-telemetry.enabled");
  }

  @Test
  @DisplayName("should fail SSL handshake")
  @SuppressWarnings("InterruptedExceptionSwallowed")
  public void testFailSslHandshake() throws Exception {
    Bootstrap bootstrap = createBootstrap(eventLoopGroup, Collections.singletonList("SSLv3"));
    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + uri.getPort());

    Throwable thrownException = null;
    CompletableFuture<Integer> result = new CompletableFuture<>();
    try {
      testing.runWithSpan(
          "parent",
          () -> {
            Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            channel.pipeline().addLast(new ClientHandler(result));
            channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
          });
      result.get(10, TimeUnit.SECONDS);
    } catch (Throwable e) {
      thrownException = e;
    }

    List<AttributeAssertion> attributeAssertion =
        Arrays.asList(
            equalTo(
                ExceptionAttributes.EXCEPTION_TYPE, thrownException.getClass().getCanonicalName()),
            equalTo(ExceptionAttributes.EXCEPTION_MESSAGE, thrownException.getMessage()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSize(4).
                hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasStatus(StatusData.error())
                        .hasParent(trace.getSpan(0))
                        .hasEventsSatisfyingExactly(
                            event -> event.hasAttributesSatisfyingExactly(attributeAssertion)),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1")),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event.hasAttributesSatisfyingExactly(
                                    satisfies(
                                        ExceptionAttributes.EXCEPTION_TYPE,
                                        v ->
                                            v.isEqualTo(
                                                SSLHandshakeException.class.getCanonicalName())),
                                    satisfies(
                                        ExceptionAttributes.EXCEPTION_STACKTRACE,
                                        v -> v.isInstanceOf(String.class)),
                                    satisfies(
                                        ExceptionAttributes.EXCEPTION_MESSAGE, v -> v.isNotNull())))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"))));
  }

  @Test
  @DisplayName("should successfully establish SSL handshake")
  @SuppressWarnings("InterruptedExceptionSwallowed")
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
          CompletableFuture<Integer> result = new CompletableFuture<>();
          channel.pipeline().addLast(new ClientHandler(result));
          channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
          result.get(10, TimeUnit.SECONDS);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSize(6)
                .hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1")),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1")),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(4)),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(5))));
  }
}
