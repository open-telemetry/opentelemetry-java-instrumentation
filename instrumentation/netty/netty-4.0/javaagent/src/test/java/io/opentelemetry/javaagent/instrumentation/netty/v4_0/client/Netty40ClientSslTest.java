/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
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
import io.netty.handler.ssl.SslHandler;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ClientSslTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static HttpClientTestServer server;
  private static EventLoopGroup eventLoopGroup;

  @BeforeAll
  static void setup() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    eventLoopGroup = new NioEventLoopGroup();
  }

  @AfterAll
  static void cleanup() throws InterruptedException, ExecutionException, TimeoutException {
    eventLoopGroup.shutdownGracefully();
    server.stop().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void shouldFailSslHandshake() {
    Bootstrap bootstrap = createBootstrap(eventLoopGroup, Collections.singletonList("SSLv3"));

    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());

    Throwable thrownException = getThrowable(bootstrap, uri, request);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasStatus(StatusData.error())
                        .hasException(thrownException),
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
                span -> {
                  span.hasName("SSL handshake")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.error());
                  span.hasAttributesSatisfyingExactly(
                      equalTo(NETWORK_TRANSPORT, "tcp"),
                      equalTo(NETWORK_TYPE, "ipv4"),
                      equalTo(NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                }));
  }

  private static Throwable getThrowable(
      Bootstrap bootstrap, URI uri, DefaultFullHttpRequest request) {
    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      Channel channel =
                          bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
                      cleanup.deferCleanup(() -> channel.close().sync());
                      CompletableFuture<Integer> result = new CompletableFuture<>();
                      channel.pipeline().addLast(new ClientHandler(result));
                      channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
                      result.get(10, TimeUnit.SECONDS);
                    }));

    // Then
    Throwable thrownException;
    if (thrown instanceof ExecutionException) {
      thrownException = thrown.getCause();
    } else {
      thrownException = thrown;
    }
    return thrownException;
  }

  @SuppressWarnings("InterruptedExceptionSwallowed")
  @Test
  public void shouldSuccessfullyEstablishSslHandshake() throws Exception {
    // given
    Bootstrap bootstrap =
        createBootstrap(eventLoopGroup, Arrays.asList("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"));

    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
          cleanup.deferCleanup(() -> channel.close().sync());
          CompletableFuture<Integer> result = new CompletableFuture<>();
          channel.pipeline().addLast(new ClientHandler(result));
          channel.writeAndFlush(request).get(10, TimeUnit.SECONDS);
          result.get(10, TimeUnit.SECONDS);
        });

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
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
                span -> {
                  span.hasName("SSL handshake").hasKind(INTERNAL).hasParent(trace.getSpan(0));
                  span.hasAttributesSatisfyingExactly(
                      equalTo(NETWORK_TRANSPORT, "tcp"),
                      equalTo(NETWORK_TYPE, "ipv4"),
                      equalTo(NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> {
                  span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0));
                },
                span -> {
                  span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(3));
                }));
  }

  // list of default ciphers copied from netty's JdkSslContext
  private static final String[] SUPPORTED_CIPHERS =
      new String[] {
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
      };

  private static Bootstrap createBootstrap(
      EventLoopGroup eventLoopGroup, List<String> enabledProtocols) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                javax.net.ssl.SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(true);
                sslEngine.setEnabledProtocols(enabledProtocols.toArray(new String[0]));
                sslEngine.setEnabledCipherSuites(SUPPORTED_CIPHERS);
                pipeline.addLast(new SslHandler(sslEngine));

                pipeline.addLast(new HttpClientCodec());
              }
            });
    return bootstrap;
  }
}
