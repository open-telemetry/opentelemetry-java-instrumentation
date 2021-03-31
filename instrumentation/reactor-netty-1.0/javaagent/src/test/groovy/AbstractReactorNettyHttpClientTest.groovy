/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
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
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    HttpClientResponse resp = createHttpClient()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(server.address.toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
      .response()
      .block()
    if (callback != null) {
      callback.accept(resp.status().code())
    }
    return resp.status().code()
  }

  abstract HttpClient createHttpClient()
}
