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
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.RequestOptions
import spock.lang.Shared

import java.util.concurrent.CompletableFuture

class VertxHttpClientTest extends HttpClientTest<Future<HttpClientRequest>> implements AgentTestTrait {

  @Shared
  def vertx = Vertx.vertx(new VertxOptions())
  @Shared
  def clientOptions = new HttpClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS)
  @Shared
  def httpClient = vertx.createHttpClient(clientOptions)

  @Override
  Future<HttpClientRequest> buildRequest(String method, URI uri, Map<String, String> headers) {
    RequestOptions requestOptions = new RequestOptions()
      .setMethod(HttpMethod.valueOf(method))
      .setAbsoluteURI(uri.toString())
    headers.each { requestOptions.putHeader(it.key, it.value) }
    return httpClient.request(requestOptions)
  }

  CompletableFuture<Integer> sendRequest(Future<HttpClientRequest> request) {
    CompletableFuture<Integer> future = new CompletableFuture<>()

    request.compose { req ->
      req.send().onComplete { asyncResult ->
        if (asyncResult.succeeded()) {
          future.complete(asyncResult.result().statusCode())
        } else {
          future.completeExceptionally(asyncResult.cause())
        }
      }
    }.onFailure { throwable ->
      future.completeExceptionally(throwable)
    }

    return future
  }

  @Override
  int sendRequest(Future<HttpClientRequest> request, String method, URI uri, Map<String, String> headers) {
    // Vertx doesn't seem to provide any synchronous API so bridge through a callback
    return sendRequest(request).get()
  }

  @Override
  void sendRequestWithCallback(Future<HttpClientRequest> request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    sendRequest(request).whenComplete { status, throwable ->
      requestResult.complete({ status }, throwable)
    }
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testReusedRequest() {
    // vertx requests can't be reused
    false
  }

  @Override
  boolean testHttps() {
    false
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    new VertxSingleConnection(host, port)
  }
}
