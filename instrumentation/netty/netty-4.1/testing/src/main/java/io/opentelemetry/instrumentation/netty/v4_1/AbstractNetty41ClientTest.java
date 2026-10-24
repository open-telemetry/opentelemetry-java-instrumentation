/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;

public abstract class AbstractNetty41ClientTest
    extends AbstractHttpClientTest<DefaultFullHttpRequest> {

  protected abstract Netty41ClientExtension clientExtension();

  protected abstract void configureChannel(Channel channel);

  @Override
  public DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    String target = uri.getPath();
    if (uri.getQuery() != null) {
      target += "?" + uri.getQuery();
    }
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), target, Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + uri.getPort());
    headers.forEach((k, v) -> request.headers().set(k, v));
    return request;
  }

  @Override
  public int sendRequest(
      DefaultFullHttpRequest defaultFullHttpRequest,
      String method,
      URI uri,
      Map<String, String> headers)
      throws InterruptedException, ExecutionException, TimeoutException {
    Channel channel =
        clientExtension().getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    configureChannel(channel);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.writeAndFlush(defaultFullHttpRequest).get();
    return result.get(20, SECONDS);
  }

  @Override
  @SuppressWarnings(
      "CatchingUnchecked") // Checked exception is thrown when connecting to unopened port
  public void sendRequestWithCallback(
      DefaultFullHttpRequest defaultFullHttpRequest,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    Channel ch;
    try {
      ch =
          clientExtension().getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      httpClientResult.complete(e);
      return;
    } catch (Exception e) {
      httpClientResult.complete(e);
      return;
    }
    configureChannel(ch);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    result.whenComplete((status, throwable) -> httpClientResult.complete(() -> status, throwable));
    if (uri.getPath().endsWith("/read-timeout")) {
      ch.pipeline().addLast(new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), MILLISECONDS));
    }
    ch.pipeline().addLast(new ClientHandler(result));
    ch.writeAndFlush(defaultFullHttpRequest);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setHttpAttributes(AbstractNetty41ClientTest::getHttpAttributes);
    optionsBuilder.setExpectedClientSpanNameMapper(
        AbstractNetty41ClientTest::getExpectedClientSpanName);
    optionsBuilder.setSingleConnectionFactory(
        (host, port) ->
            new SingleNettyConnection(
                clientExtension().buildBootstrap(false, false),
                host,
                port,
                this::configureChannel));

    optionsBuilder.disableTestRedirects();
    optionsBuilder.spanEndsAfterBody();
    // On Windows, non-routable addresses behave differently (SSL handshake vs CONNECT span,
    // or no error thrown), causing platform-specific test failures.
    if (OS.WINDOWS.isCurrentOs()) {
      optionsBuilder.disableTestRemoteConnection();
    }
  }

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, https://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "https://192.0.2.1/".equals(uriString)) {
      return emptySet();
    }
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(SERVER_ADDRESS);
    attributes.remove(SERVER_PORT);
    return attributes;
  }

  private static String getExpectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }

  private static int getPort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    } else if ("http".equals(uri.getScheme())) {
      return 80;
    } else if ("https".equals(uri.getScheme())) {
      return 443;
    } else {
      throw new IllegalArgumentException("Unexpected uri: " + uri);
    }
  }

  @Test
  void pipelinedRequests()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    try (ServerSocket server = new ServerSocket(PortUtils.findOpenPort())) {
      CompletableFuture<Void> served =
          CompletableFuture.runAsync(
              () -> {
                try (Socket socket = server.accept()) {
                  socket.setSoTimeout(20_000);
                  BufferedReader reader =
                      new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
                  // Do not send either response until both requests have arrived.
                  for (int i = 0; i < 2; i++) {
                    String line;
                    do {
                      line = reader.readLine();
                    } while (line != null && !line.isEmpty());
                  }
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n"
                                  + "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n")
                              .getBytes(US_ASCII));
                  socket.getOutputStream().flush();
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      URI uri = URI.create("http://localhost:" + server.getLocalPort() + "/pipeline");
      Channel channel =
          clientExtension()
              .getBootstrap(uri)
              .connect("localhost", server.getLocalPort())
              .sync()
              .channel();
      List<SpanContext> callbacks = new ArrayList<>();
      CompletableFuture<Void> received = new CompletableFuture<>();
      channel
          .pipeline()
          .addLast(
              new SimpleChannelInboundHandler<HttpObject>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
                  if (msg instanceof HttpResponse) {
                    callbacks.add(Span.current().getSpanContext());
                    if (callbacks.size() == 2) {
                      received.complete(null);
                    }
                  }
                }
              });
      List<SpanContext> parents = new ArrayList<>();
      try {
        for (int i = 0; i < 2; i++) {
          Span parent =
              testing.getOpenTelemetry().getTracer("test").spanBuilder("parent-" + i).startSpan();
          parents.add(parent.getSpanContext());
          try (Scope ignored = Context.current().with(parent).makeCurrent()) {
            configureChannel(channel);
            channel.writeAndFlush(buildRequest("GET", uri, emptyMap())).sync();
          } finally {
            parent.end();
          }
        }
        received.get(20, SECONDS);
        served.get(20, SECONDS);
        assertThat(callbacks).containsExactlyElementsOf(parents);
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent-0"),
                    span ->
                        span.hasName("GET")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(equalTo(HTTP_RESPONSE_STATUS_CODE, 201))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent-1"),
                    span ->
                        span.hasName("GET")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(equalTo(HTTP_RESPONSE_STATUS_CODE, 202))));
      } finally {
        channel.close().sync();
      }
    }
  }

  @Test
  void closeChannel() throws InterruptedException {
    String method = "GET";
    URI uri = resolveAddress("/read-timeout");
    DefaultFullHttpRequest request = buildRequest(method, uri, emptyMap());

    Channel channel =
        clientExtension().getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    configureChannel(channel);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel.pipeline().addLast(new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), MILLISECONDS));
    channel.writeAndFlush(request).sync();
    Thread.sleep(1_000);
    channel.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent(),
                span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))));
  }
}
