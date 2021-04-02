/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.reactivex.Single
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import java.util.function.Consumer
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(10)
class VertxRxWebClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  def clientOptions = new WebClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS)
  @Shared
  WebClient client = WebClient.create(vertx, clientOptions)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    return sendRequest(method, uri, headers).blockingGet().statusCode()
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    sendRequest(method, uri, headers)
      .subscribe(new io.reactivex.functions.Consumer<HttpResponse<?>>() {
        @Override
        void accept(HttpResponse<?> httpResponse) throws Exception {
          callback.accept(httpResponse.statusCode())
        }
      })
  }

  private Single<HttpResponse<?>> sendRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request.rxSend()
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
  boolean testAsyncWithParent() {
    //Make rxjava2 instrumentation work with vert.x reactive in order to fix this test
    return false
  }
}
