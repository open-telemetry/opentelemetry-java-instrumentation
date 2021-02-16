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
import io.vertx.reactivex.ext.web.client.WebClient
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
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
      .rxSend()
      .doOnSuccess { response -> callback?.call() }
      .map { it.statusCode() }
      .toObservable()
      .blockingFirst()
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
}
