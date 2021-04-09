/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class SpringRestTemplateTest extends HttpClientTest<Void> implements AgentTestTrait {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
  }

  @Override
  Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  int sendRequest(Void request, String method, URI uri, Map<String, String> headers) {
    def httpHeaders = new HttpHeaders()
    headers.each { httpHeaders.put(it.key, [it.value]) }
    def requestEntity = new HttpEntity<String>(httpHeaders)
    try {
      return restTemplate.exchange(uri, HttpMethod.valueOf(method), requestEntity, String)
        .statusCode
        .value()
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void sendRequestWithCallback(Void request, String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    restTemplate.execute(uri, HttpMethod.valueOf(method), { req ->
      headers.forEach(req.getHeaders().&add)
    }, { response ->
      callback.accept(response.statusCode.value())
    })
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }
}
