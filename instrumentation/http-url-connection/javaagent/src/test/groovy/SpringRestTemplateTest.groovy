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

class SpringRestTemplateTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = buildRequest(headers)
    return sendRequest(request, method, uri)
  }

  @Override
  int doReusedRequest(String method, URI uri) {
    def request = buildRequest([:])
    sendRequest(request, method, uri)
    return sendRequest(request, method, uri)
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    restTemplate.execute(uri, HttpMethod.valueOf(method), { request ->
      headers.forEach(request.getHeaders().&add)
    }, { response ->
      callback.accept(response.statusCode.value())
    })
  }

  private int sendRequest(HttpEntity<String> request, String method, URI uri) {
    try {
      return restTemplate.exchange(uri, HttpMethod.valueOf(method), request, String)
        .statusCode
        .value()
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  private static HttpEntity<String> buildRequest(Map<String, String> headers) {
    def httpHeaders = new HttpHeaders()
    headers.each { httpHeaders.put(it.key, [it.value]) }
    return new HttpEntity<String>(httpHeaders)
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
