/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientResponse

abstract class AbstractReactorNettyHttpClientTest extends HttpClientTest implements AgentTestTrait {

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
    false
  }

  @Override
  String userAgent() {
    return "ReactorNetty"
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    Mono<HttpClientResponse> response = sendRequest(method, uri, headers)
    return response.block().status().code()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    Mono<HttpClientResponse> response = sendRequest(method, uri, headers)
    response.subscribe {
      callback.accept(it.status().code())
    }
  }

  Mono<HttpClientResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    return createHttpClient()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(server.address.toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
      .response()
  }

  abstract HttpClient createHttpClient()
}
