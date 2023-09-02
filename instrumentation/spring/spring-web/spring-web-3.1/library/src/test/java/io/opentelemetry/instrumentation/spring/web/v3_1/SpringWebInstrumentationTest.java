/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class SpringWebInstrumentationTest extends AbstractHttpClientTest<HttpEntity<String>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  static RestTemplate restTemplate;

  @BeforeAll
  static void setUp() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis());
    restTemplate = new RestTemplate(requestFactory);
    restTemplate
        .getInterceptors()
        .add(
            SpringWebTelemetry.builder(testing.getOpenTelemetry())
                .setCapturedRequestHeaders(
                    Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
                .setCapturedResponseHeaders(
                    Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
                .build()
                .newInterceptor());
  }

  @Override
  public HttpEntity<String> buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpHeaders httpHeaders = new HttpHeaders();
    headers.forEach((k, v) -> httpHeaders.put(k, singletonList(v)));
    return new HttpEntity<>(httpHeaders);
  }

  @Override
  public int sendRequest(
      HttpEntity<String> request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    try {
      return restTemplate
          .exchange(uri, HttpMethod.valueOf(method), request, String.class)
          .getStatusCode()
          .value();
    } catch (ResourceAccessException exception) {
      throw (Exception) exception.getCause();
    }
  }

  @Override
  public void sendRequestWithCallback(
      HttpEntity<String> request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    try {
      restTemplate.execute(
          uri,
          HttpMethod.valueOf(method),
          req -> headers.forEach(req.getHeaders()::add),
          response -> {
            httpClientResult.complete(response.getStatusCode().value());
            return null;
          });
    } catch (ResourceAccessException exception) {
      httpClientResult.complete(exception.getCause());
    }
  }

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestCircularRedirects();
    optionsBuilder.disableTestReadTimeout();
    optionsBuilder.disableTestNonStandardHttpMethod();
    if (SemconvStability.emitOldHttpSemconv()) {
      optionsBuilder.setHttpAttributes(
          uri -> {
            Set<AttributeKey<?>> attributes =
                new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_NAME);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_VERSION);
            return attributes;
          });
    }
  }
}
