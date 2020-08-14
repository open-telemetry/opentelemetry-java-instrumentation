/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package client


import io.opentelemetry.auto.test.base.HttpClientTest
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Timeout

@Timeout(5)
class SpringWebfluxHttpClientTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    ClientResponse response = WebClient.builder().build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
      .exchange()
      .doAfterSuccessOrError { res, ex ->
        callback?.call()
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
