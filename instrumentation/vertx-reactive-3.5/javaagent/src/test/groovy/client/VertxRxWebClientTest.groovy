/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.ext.web.client.HttpRequest
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import java.util.function.Consumer
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
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
  }

  @Override
  int sendRequest(HttpRequest<Buffer> request, String method, URI uri, Map<String, String> headers) {
    return request.rxSend().blockingGet().statusCode()
  }

  @Override
  void sendRequestWithCallback(HttpRequest<Buffer> request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
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
