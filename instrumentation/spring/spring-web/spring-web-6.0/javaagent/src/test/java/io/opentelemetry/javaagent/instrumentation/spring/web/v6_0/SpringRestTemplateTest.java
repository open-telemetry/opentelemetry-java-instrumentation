/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.web.v6_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class SpringRestTemplateTest extends AbstractHttpClientTest<HttpEntity<String>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.http.client.emit-experimental-telemetry");

  private static final RestTemplate restTemplate = buildClient(false);
  private static final RestTemplate restTemplateWithReadTimeout = buildClient(true);

  @SuppressWarnings("PreferJavaTimeOverload")
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
          .exchange(uri.toString(), HttpMethod.valueOf(method), request, String.class)
          .getStatusCode()
          .value();
    } catch (ResourceAccessException e) {
      throw (Exception) e.getCause();
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
      getClient(uri)
          .execute(
              uri.toString(),
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
    } catch (ResourceAccessException e) {
      httpClientResult.complete(e.getCause());
    }
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setMaxRedirects(20);

    // no enum value for TEST
    optionsBuilder.disableTestNonStandardHttpMethod();
    if (EXPERIMENTAL_ATTRIBUTES) {
      optionsBuilder.setExpectedClientSpanNameMapper(
          (uri, method) -> method + " " + getTemplate(uri));
      optionsBuilder.setExpectedUrlTemplateMapper(SpringRestTemplateTest::getTemplate);
    }
  }

  private static String getTemplate(URI uri) {
    String path = uri.getPath();
    if (path.startsWith("/hello/")) {
      return "/hello/{name}";
    }
    return path;
  }

  @Test
  void requestWithTemplate() {
    URI rootUri = resolveAddress("/");
    URI uri = resolveAddress("/hello/world");
    String method = "GET";
    int responseCode =
        restTemplate
            .exchange(
                rootUri + "hello/{name}", HttpMethod.valueOf(method), null, String.class, "world")
            .getStatusCode()
            .value();

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertClientSpan(span, uri, method, responseCode, null)
                        .hasNoParent()
                        .hasStatus(StatusData.unset()),
                span -> assertServerSpan(span).hasParent(trace.getSpan(0))));
  }
}
