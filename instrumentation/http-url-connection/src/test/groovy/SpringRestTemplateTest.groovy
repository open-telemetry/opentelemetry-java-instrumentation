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

import io.opentelemetry.auto.test.base.HttpClientTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class SpringRestTemplateTest extends HttpClientTest {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def httpHeaders = new HttpHeaders()
    headers.each { httpHeaders.put(it.key, [it.value]) }
    def request = new HttpEntity<String>(httpHeaders)
    ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.resolve(method), request, String)
    callback?.call()
    return response.statusCode.value()
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // FIXME: exception wrapped in ResourceAccessException
    return false
  }
}
