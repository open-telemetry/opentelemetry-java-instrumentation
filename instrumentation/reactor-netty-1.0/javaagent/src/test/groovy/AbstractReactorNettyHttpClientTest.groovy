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
    request.response().subscribe {
      callback.accept(it.status().code())
    }
  }

  private HttpClient.ResponseReceiver buildRequest(String method, URI uri, Map<String, String> headers) {
    return createHttpClient()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(server.address.toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
  }

  private static int sendRequest(HttpClient.ResponseReceiver request) {
    return request.response().block().status().code()
  }

  abstract HttpClient createHttpClient()
}
