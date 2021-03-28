/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.test.base.SingleConnection
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientResponse

class ReactorNettyHttpClientTest extends HttpClientTest implements AgentTestTrait {

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
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Closure callback = null) {
    HttpClientResponse resp = HttpClient.create()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(server.address.toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
      .response()
      .block()
    if (callback != null) {
      callback.call()
    }
    return resp.status().code()
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    def httpClient = HttpClient
      .newConnection()
      .host(host)
      .port(port)

    return new SingleConnection() {

      @Override
      int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
        return httpClient
          .headers({ h -> headers.each { k, v -> h.add(k, v) } })
          .get()
          .uri(path)
          .response()
          .block()
          .status().code()
      }
    }
  }
}
