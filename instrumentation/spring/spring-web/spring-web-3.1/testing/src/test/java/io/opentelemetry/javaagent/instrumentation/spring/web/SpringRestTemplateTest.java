/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.web;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class SpringRestTemplateTest extends AbstractHttpClientTest<HttpEntity<String>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  static RestTemplate restTemplate = buildClient(false);
  static RestTemplate restTemplateWithReadTimeout = buildClient(true);

  private static RestTemplate buildClient(boolean readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) CONNECTION_TIMEOUT.toMillis());
    if (readTimeout) {
      factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
    }
    return new RestTemplate(factory);
  }

  private static RestTemplate getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return restTemplateWithReadTimeout;
    }
    return restTemplate;
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
      return getClient(uri)
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
            byte[] buffer = new byte[1024];
            try (InputStream inputStream = response.getBody()) {
              while (inputStream.read(buffer) >= 0) {}
            }
            httpClientResult.complete(response.getStatusCode().value());
            return null;
          });
    } catch (ResourceAccessException exception) {
      httpClientResult.complete(exception.getCause());
    }
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setMaxRedirects(20);
  }
}
