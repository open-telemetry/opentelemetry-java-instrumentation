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

class SpringWebfluxHttpClientTest extends HttpClientTest<WebClient.RequestBodySpec> implements AgentTestTrait {

  @Override
  WebClient.RequestBodySpec buildRequest(String method, URI uri, Map<String, String> headers) {
    return WebClient.builder().build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
  }

  @Override
  int sendRequest(WebClient.RequestBodySpec request, String method, URI uri, Map<String, String> headers) {
    return request.exchange().block().statusCode().value()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = buildRequest(method, uri, headers)
    request.exchange().subscribe {
      callback.accept(it.statusCode().value())
    }
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
