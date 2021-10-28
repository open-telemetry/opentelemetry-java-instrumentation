/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.AsyncResult
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.HttpRequest
import io.vertx.reactivex.ext.web.client.WebClient
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class VertxRxCircuitBreakerWebClientTest extends HttpClientTest<HttpRequest<?>> implements AgentTestTrait {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  def clientOptions = new WebClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS)
  @Shared
  WebClient client = WebClient.create(vertx, clientOptions)
  @Shared
  CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
    new CircuitBreakerOptions()
      .setTimeout(-1) // Disable the timeout otherwise it makes each test take this long.
  )

  @Override
  HttpRequest<?> buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.request(HttpMethod.valueOf(method), getPort(uri), uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
  }

  @Override
  int sendRequest(HttpRequest<?> request, String method, URI uri, Map<String, String> headers) {
    // VertxRx doesn't seem to provide a synchronous API at all for circuit breaker. Bridge through
    // a callback.
    CompletableFuture<Integer> future = new CompletableFuture<>()
    sendRequestWithCallback(request) {
      if (it.succeeded()) {
        future.complete(it.result().statusCode())
      } else {
        future.completeExceptionally(it.cause())
      }
    }
    return future.get()
  }

  void sendRequestWithCallback(HttpRequest<?> request, Consumer<AsyncResult> consumer) {
    breaker.executeCommand({ command ->
      request.rxSend().doOnSuccess {
        command.complete(it)
      }.doOnError {
        command.fail(it)
      }.subscribe()
    }, {
      consumer.accept(it)
    })
  }

  @Override
  void sendRequestWithCallback(HttpRequest<?> request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    sendRequestWithCallback(request) {
      if (it.succeeded()) {
        requestResult.complete(it.result().statusCode())
      } else {
        requestResult.complete(it.cause())
      }
    }
  }

  @Override
  String userAgent() {
    return "Vert.x-WebClient"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testHttps() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    def attributes = super.httpAttributes(uri)
    attributes.remove(SemanticAttributes.HTTP_FLAVOR)
    attributes.remove(SemanticAttributes.NET_PEER_NAME)
    attributes.remove(SemanticAttributes.NET_PEER_PORT)
    return attributes
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    return new VertxRxCircuitBreakerSingleConnection(host, port, breaker)
  }
}
