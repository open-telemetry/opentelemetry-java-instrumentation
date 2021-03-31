/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Timeout

@Timeout(5)
class SpringWebfluxHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    ClientResponse response = WebClient.builder().build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
      .exchange()
      .doAfterSuccessOrError { res, ex ->
        callback?.accept(res.statusCode().value())
      }
      .block()

    response.statusCode().value()
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }


  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }
}
