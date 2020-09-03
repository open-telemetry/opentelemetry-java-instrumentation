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
import spock.lang.Shared
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit

@Timeout(5)
abstract class JdkHttpClientTest extends HttpClientTest {

  @Shared
  def client = HttpClient.newBuilder().connectTimeout(Duration.of(CONNECT_TIMEOUT_MS,
    ChronoUnit.MILLIS)).build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {

    def builder = HttpRequest.newBuilder().uri(uri).method(method, HttpRequest.BodyPublishers.noBody())

    headers.entrySet().each {
      builder.header(it.key, it.value)
    }
    def request = builder.build()

    def resp = send(request)
    callback?.call()
    return resp.statusCode()
  }

  abstract HttpResponse send(HttpRequest request)

  @Override
  boolean testRedirects() {
    // Generates 4 spans
    false
  }
}
