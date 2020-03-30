/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture

@Timeout(10)
class VertxHttpClientTest extends HttpClientTest {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  HttpClient httpClient = vertx.createHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    CompletableFuture<HttpClientResponse> future = new CompletableFuture<>()
    def request = httpClient.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    request.handler { response ->
      callback?.call()
      future.complete(response)
    }
    request.end()

    return future.get().statusCode()
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }
}
