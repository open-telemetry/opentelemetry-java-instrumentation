/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.test.base.HttpClientTest.getPort;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractNetty41ClientTest
    extends AbstractHttpClientTest<DefaultFullHttpRequest> {

  protected abstract Netty41ClientExtension clientExtension();

  protected abstract void configureChannel(Channel channel);

  @Override
  protected boolean testRedirects() {
    return false;
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
  protected void sendRequestWithCallback(
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
    ch.pipeline().addLast(new ClientHandler(result));
    ch.writeAndFlush(defaultFullHttpRequest);
  }

  @Override
  protected String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return super.expectedClientSpanName(uri, method);
    }
  }

  @Override
  protected Set<AttributeKey<?>> httpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, https://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "https://192.0.2.1/".equals(uriString)) {
      return Collections.emptySet();
    }
    Set<AttributeKey<?>> attributes = super.httpAttributes(uri);
    attributes.remove(SemanticAttributes.NET_PEER_NAME);
    attributes.remove(SemanticAttributes.NET_PEER_PORT);
    return attributes;
  }

  @Override
  protected SingleConnection createSingleConnection(String host, int port) {
    return new SingleNettyConnection(
        clientExtension().buildBootstrap(false, false), host, port, this::configureChannel);
  }
}
