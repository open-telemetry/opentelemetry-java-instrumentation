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
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.ext.web.client.HttpRequest
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import spock.lang.Shared

class VertxRxWebClientTest extends HttpClientTest<HttpRequest<Buffer>> implements AgentTestTrait {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  def clientOptions = new WebClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS)
  @Shared
  WebClient client = WebClient.create(vertx, clientOptions)

  @Override
  HttpRequest<Buffer> buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.requestAbs(HttpMethod.valueOf(method), "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
  }

  @Override
  int sendRequest(HttpRequest<Buffer> request, String method, URI uri, Map<String, String> headers) {
    return request.rxSend().blockingGet().statusCode()
  }

  @Override
  void sendRequestWithCallback(HttpRequest<Buffer> request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    request.rxSend()
      .subscribe(new io.reactivex.functions.Consumer<HttpResponse<?>>() {
        @Override
        void accept(HttpResponse<?> httpResponse) throws Exception {
          requestResult.complete(httpResponse.statusCode())
        }
      }, new io.reactivex.functions.Consumer<Throwable>() {
        @Override
        void accept(Throwable throwable) throws Exception {
          requestResult.complete(throwable)
        }
      })
  }

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    if (exception.class == RuntimeException) {
      switch (uri.toString()) {
        case "http://localhost:61/": // unopened port
        case "http://192.0.2.1/": // non routable address
          exception = exception.getCause()
      }
    }
    return exception
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
    return new VertxRxSingleConnection(host, port)
  }
}
