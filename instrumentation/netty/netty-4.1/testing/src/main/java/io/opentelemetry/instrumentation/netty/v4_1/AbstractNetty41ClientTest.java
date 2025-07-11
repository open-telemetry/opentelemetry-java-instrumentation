/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

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
    return result.get(20, TimeUnit.SECONDS);
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
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return;
    } catch (Exception exception) {
      httpClientResult.complete(exception);
      return;
    }
    configureChannel(ch);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    result.whenComplete((status, throwable) -> httpClientResult.complete(() -> status, throwable));
    if (uri.toString().contains("/read-timeout")) {
      ch.pipeline().addLast(new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
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
  }

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, https://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "https://192.0.2.1/".equals(uriString)) {
      return Collections.emptySet();
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
  void closeChannel() throws ExecutionException, InterruptedException {
    String method = "GET";
    URI uri = resolveAddress("/read-timeout");
    DefaultFullHttpRequest request = buildRequest(method, uri, Collections.emptyMap());

    Channel channel =
        clientExtension().getBootstrap(uri).connect(uri.getHost(), getPort(uri)).sync().channel();
    configureChannel(channel);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    channel.pipeline().addLast(new ClientHandler(result));
    channel
        .pipeline()
        .addLast(new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
    channel.writeAndFlush(request).get();
    Thread.sleep(1_000);
    channel.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent(),
                span -> span.hasName("test-http-server").hasParent(trace.getSpan(0))));
  }
}
