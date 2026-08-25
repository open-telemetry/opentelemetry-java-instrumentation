/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttpClientTest extends AbstractHttpClientTest<HttpClientRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final Vertx vertx = Vertx.vertx(new VertxOptions());
  private final HttpClient httpClient = buildClient(vertx);

  private static HttpClient buildClient(Vertx vertx) {
    HttpClientOptions clientOptions =
        new HttpClientOptions().setConnectTimeout(Math.toIntExact(CONNECTION_TIMEOUT.toMillis()));
    return vertx.createHttpClient(clientOptions);
  }

  @AfterAll
  void closeVertx() {
    vertx.close();
  }

  @Override
  public HttpClientRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClientRequest request = httpClient.requestAbs(HttpMethod.valueOf(method), uri.toString());
    headers.forEach(request::putHeader);
    return request;
  }

  private static CompletableFuture<Integer> sendRequest(HttpClientRequest request) {
    CompletableFuture<Integer> future = new CompletableFuture<>();
    request
        .handler(response -> future.complete(response.statusCode()))
        .exceptionHandler(future::completeExceptionally)
        .end();
    return future;
  }

  @Override
  public int sendRequest(
      HttpClientRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    // Vertx doesn't seem to provide any synchronous API so bridge through a callback
    return sendRequest(request).get(30, SECONDS);
  }

  @Override
  public void sendRequestWithCallback(
      HttpClientRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    sendRequest(request)
        .whenComplete((status, throwable) -> httpClientResult.complete(() -> status, throwable));
  }

  @Test
  void endHandlerDoesNotCountAsWriteAttempt() throws Exception {
    URI uri = resolveAddress("/success");
    HttpClientRequest request = httpClient.requestAbs(HttpMethod.GET, uri.toString());
    CompletableFuture<Integer> result = new CompletableFuture<>();
    request
        .handler(response -> result.complete(response.statusCode()))
        .exceptionHandler(result::completeExceptionally);

    testing.runWithHttpClientSpan(
        "parent-client-span",
        () -> {
          request.endHandler(ignored -> {});
        });
    request.end();

    assertThat(result.get(30, SECONDS)).isEqualTo(200);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent-client-span", "GET"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-client-span").hasKind(CLIENT).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(CLIENT).hasNoParent(),
                span -> span.hasKind(SERVER).hasParent(trace.getSpan(0))));
  }

  @Test
  void doesNotInjectAfterSuppressedSendHead() throws Exception {
    URI uri = resolveAddress("/success");
    HttpClientRequest request = httpClient.requestAbs(HttpMethod.GET, uri.toString());
    CompletableFuture<Integer> result = new CompletableFuture<>();
    request
        .handler(response -> result.complete(response.statusCode()))
        .exceptionHandler(result::completeExceptionally);

    testing.runWithHttpClientSpan(
        "parent-client-span",
        () -> {
          request.sendHead();
        });
    request.end();

    assertThat(result.get(30, SECONDS)).isEqualTo(200);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent-client-span", "test-http-server"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-client-span").hasKind(CLIENT).hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span)));
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.disableTestHttps();
    optionsBuilder.disableTestReadTimeout();
    optionsBuilder.disableTestNonStandardHttpMethod();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          attributes.remove(SERVER_ADDRESS);
          attributes.remove(SERVER_PORT);
          return attributes;
        });

    optionsBuilder.setSingleConnectionFactory(VertxHttpClientTest::createSingleConnection);
  }

  private static SingleConnection createSingleConnection(String host, int port) {
    // This test fails on Vert.x 3.0 and only works starting from 3.1
    // Most probably due to https://github.com/eclipse-vertx/vert.x/pull/1126
    boolean shouldRun = testLatestDeps();
    return shouldRun ? new VertxSingleConnection(host, port) : null;
  }
}
