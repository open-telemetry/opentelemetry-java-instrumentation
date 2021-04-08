/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient

class SpringWebfluxHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    def request = buildRequest(method, uri, headers)
    return sendRequest(request)
  }

  @Override
  int doReusedRequest(String method, URI uri) {
    def request = buildRequest(method, uri, [:])
    sendRequest(request)
    return sendRequest(request)
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = buildRequest(method, uri, headers)
    request.exchange().subscribe {
      callback.accept(it.statusCode().value())
    }
  }

  private static WebClient.RequestBodySpec buildRequest(String method, URI uri, headers) {
    return WebClient.builder().build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
  }

  private static int sendRequest(WebClient.RequestBodySpec request) {
    return request.exchange().block().statusCode().value()
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }
}
