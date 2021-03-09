/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.springframework.http.HttpMethod
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
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    try {
      return restTemplate.execute(uri, HttpMethod.valueOf(method), { request ->
        headers.forEach(request.getHeaders().&add)
      }, { response ->
        callback?.call()
        response.statusCode.value()
      })
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }
}
