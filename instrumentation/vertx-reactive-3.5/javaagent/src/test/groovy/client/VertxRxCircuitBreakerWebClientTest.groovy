/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.HttpRequest
import io.vertx.reactivex.ext.web.client.WebClient
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import spock.lang.Shared

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
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
  }

  @Override
  int sendRequest(HttpRequest<?> request, String method, URI uri, Map<String, String> headers) {
    // VertxRx doesn't seem to provide a synchronous API at all for circuit breaker. Bridge through
    // a callback.
    CompletableFuture<Integer> future = new CompletableFuture<>()
    internalSendRequest(request) {
      future.complete(it)
    }
    return future.get()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = buildRequest(method, uri, headers)
    internalSendRequest(request, callback)
  }

  private void internalSendRequest(HttpRequest<?> request, Consumer<Integer> callback) {
    breaker.executeCommand({ command ->
      request.rxSend().doOnSuccess {
        command.complete(it)
      }.doOnError {
        command.fail(it)
      }.subscribe()
    }, {
      callback.accept(it.result().statusCode())
    })
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
  boolean testConnectionFailure() {
    false
  }

  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }

  @Override
  boolean testCausality() {
    false
  }

  @Override
  boolean testCallbackWithParent() {
    //Make rxjava2 instrumentation work with vert.x reactive in order to fix this test
    return false
  }

}
