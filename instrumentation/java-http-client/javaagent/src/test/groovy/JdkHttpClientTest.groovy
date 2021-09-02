/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import spock.lang.Shared

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit

class JdkHttpClientTest extends HttpClientTest<HttpRequest> implements AgentTestTrait {

  @Shared
  def client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.of(CONNECT_TIMEOUT_MS, ChronoUnit.MILLIS))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  @Override
  HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def requestBuilder = HttpRequest.newBuilder()
      .uri(uri)
      .method(method, HttpRequest.BodyPublishers.noBody())
    headers.entrySet().each {
      requestBuilder.header(it.key, it.value)
    }
    return requestBuilder.build()
  }

  @Override
  int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
  }

  @Override
  void sendRequestWithCallback(HttpRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .whenComplete { response, throwable ->
        requestResult.complete({ response.statusCode() }, throwable?.getCause())
      }
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  // TODO nested client span is not created, but context is still injected
  //  which is not what the test expects
  @Override
  boolean testWithClientParent() {
    false
  }
}
