/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxRxCircuitBreakerWebClientTest extends AbstractHttpClientTest<HttpRequest<?>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final Vertx vertx = Vertx.vertx(new VertxOptions());
  private final WebClient httpClient = buildClient(vertx);
  private final CircuitBreaker breaker =
      CircuitBreaker.create(
          "my-circuit-breaker",
          vertx,
          new CircuitBreakerOptions()
              .setTimeout(-1) // Disable the timeout otherwise it makes each test take this long.
          );

  private static WebClient buildClient(Vertx vertx) {
    WebClientOptions clientOptions =
        new WebClientOptions().setConnectTimeout(Math.toIntExact(CONNECTION_TIMEOUT.toMillis()));
    return WebClient.create(vertx, clientOptions);
  }

  @Override
  public HttpRequest<Buffer> buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpRequest<Buffer> request = httpClient.requestAbs(HttpMethod.valueOf(method), uri.toString());
    headers.forEach(request::putHeader);
    return request;
  }

  @Override
  public int sendRequest(
      HttpRequest<?> request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    // VertxRx doesn't seem to provide a synchronous API at all for circuit breaker. Bridge through
    // a callback.
    CompletableFuture<Integer> future = new CompletableFuture<>();
    sendRequestWithCallback(
        request,
        result -> {
          if (result.succeeded()) {
            future.complete(result.result().statusCode());
          } else {
            future.completeExceptionally(result.cause());
          }
        });

    return future.get();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void sendRequestWithCallback(
      HttpRequest<?> request, Consumer<AsyncResult<HttpResponse<?>>> consumer) {
    breaker.execute(
        command -> request.rxSend().subscribe(command::complete, command::fail), consumer::accept);
  }

  @Override
  public void sendRequestWithCallback(
      HttpRequest<?> request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    sendRequestWithCallback(
        request,
        result -> {
          if (result.succeeded()) {
            requestResult.complete(result.result().statusCode());
          } else {
            requestResult.complete(result.cause());
          }
        });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.disableTestHttps();
    optionsBuilder.disableTestReadTimeout();
    optionsBuilder.setHttpAttributes(VertxRxCircuitBreakerWebClientTest::getHttpAttributes);
    optionsBuilder.setExpectedClientSpanNameMapper(
        VertxRxCircuitBreakerWebClientTest::expectedClientSpanName);
    optionsBuilder.setSingleConnectionFactory(
        (host, port) -> new VertxRxCircuitBreakerSingleConnection(host, port, breaker));
  }

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return Collections.emptySet();
      default:
        return HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES;
    }
  }

  private static String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }
}
