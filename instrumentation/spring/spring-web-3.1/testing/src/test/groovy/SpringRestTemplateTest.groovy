/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class SpringRestTemplateTest extends HttpClientTest<HttpEntity<String>> implements AgentTestTrait {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
  }

  @Override
  HttpEntity<String> buildRequest(String method, URI uri, Map<String, String> headers) {
    def httpHeaders = new HttpHeaders()
    headers.each { httpHeaders.put(it.key, [it.value]) }
    return new HttpEntity<String>(httpHeaders)
  }

  @Override
  int sendRequest(HttpEntity<String> request, String method, URI uri, Map<String, String> headers) {
    try {
      return restTemplate.exchange(uri, HttpMethod.valueOf(method), request, String)
        .statusCode
        .value()
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void sendRequestWithCallback(HttpEntity<String> request, String method, URI uri, Map<String, String> headers = [:], AbstractHttpClientTest.RequestResult requestResult) {
    try {
      restTemplate.execute(uri, HttpMethod.valueOf(method), { req ->
        req.getHeaders().putAll(request.getHeaders())
      }, { response ->
        // read request body to avoid broken pipe errors on the server side
        byte[] buffer = new byte[1024]
        try (InputStream inputStream = response.body) {
          while (inputStream.read(buffer) >= 0) {
          }
        }
        requestResult.complete(response.statusCode.value())
      })
    } catch (ResourceAccessException exception) {
      requestResult.complete(exception.getCause())
    }
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer responseCodeOnRedirectError() {
    return 302
  }
}
