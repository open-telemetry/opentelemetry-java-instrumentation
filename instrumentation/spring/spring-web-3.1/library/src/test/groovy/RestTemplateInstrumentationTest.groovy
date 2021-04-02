/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class RestTemplateInstrumentationTest extends HttpClientTest implements LibraryTestTrait {
  @Shared
  RestTemplate restTemplate

  def setupSpec() {
    if (restTemplate == null) {
      restTemplate = new RestTemplate()
      restTemplate.getInterceptors().add(new RestTemplateInterceptor(getOpenTelemetry()))
    }
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    try {
      def httpHeaders = new HttpHeaders()
      headers.each { httpHeaders.put(it.key, [it.value]) }
      def request = new HttpEntity<String>(httpHeaders)
      ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.valueOf(method), request, String)
      return response.statusCode.value()
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    // This is not actually an asynchronous invocation since the response handler is always invoked
    // inline. But context propagation works the same for a response handler as a callback so we
    // treat it as an async test.
    restTemplate.execute(uri, HttpMethod.valueOf(method), { request ->
      headers.forEach(request.getHeaders().&add)
    }, { response ->
      callback.accept(response.statusCode.value())
    })
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }

  // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
  @Override
  boolean testWithClientParent() {
    false
  }
}
