/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.spring.web.SpringWebTelemetry
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class SpringWebInstrumentationTest extends HttpClientTest<HttpEntity<String>> implements LibraryTestTrait {
  @Shared
  RestTemplate restTemplate

  def setupSpec() {
    if (restTemplate == null) {
      def requestFactory = new SimpleClientHttpRequestFactory()
      requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS)
      restTemplate = new RestTemplate(requestFactory)
      restTemplate.getInterceptors().add(SpringWebTelemetry.create(getOpenTelemetry()).newInterceptor())
    }
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
  void sendRequestWithCallback(HttpEntity<String> request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    try {
      restTemplate.execute(uri, HttpMethod.valueOf(method), { req ->
        headers.forEach(req.getHeaders().&add)
      }, { response ->
        requestResult.complete(response.statusCode.value())
      })
    } catch (ResourceAccessException exception) {
      requestResult.complete(exception.getCause())
    }
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    def attributes = super.httpAttributes(uri)
    attributes.remove(SemanticAttributes.HTTP_FLAVOR)
    attributes.add(SemanticAttributes.HTTP_SCHEME)
    attributes
  }
}
