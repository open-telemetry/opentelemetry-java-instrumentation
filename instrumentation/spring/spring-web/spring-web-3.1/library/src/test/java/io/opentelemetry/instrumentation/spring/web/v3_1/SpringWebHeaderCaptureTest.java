/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

class SpringWebHeaderCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesHeadersMatchingSelectorPatterns() throws Exception {
    ClientHttpRequestInterceptor interceptor =
        SpringWebTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build()
            .createInterceptor();

    sendRequest(interceptor);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .containsExactly("request-value");
    assertThat(attributes.get(stringArrayKey("http.request.header.x-secret-token"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .containsExactly("response-value");
    assertThat(attributes.get(stringArrayKey("http.response.header.x-secret-token"))).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void capturesHeadersConfiguredByName() throws Exception {
    ClientHttpRequestInterceptor interceptor =
        SpringWebTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build()
            .createInterceptor();

    sendRequest(interceptor);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .containsExactly("request-value");
    assertThat(attributes.get(stringArrayKey("http.request.header.x-secret-token"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .containsExactly("response-value");
    assertThat(attributes.get(stringArrayKey("http.response.header.x-secret-token"))).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    ClientHttpRequestInterceptor interceptor =
        SpringWebTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createInterceptor();

    sendRequest(interceptor);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    // "*" is a legal header name character, so it names a header rather than matching all of
    // them
    assertThat(attributes.asMap().keySet())
        .noneMatch(key -> key.getKey().startsWith("http.request.header."))
        .noneMatch(key -> key.getKey().startsWith("http.response.header."));
  }

  private static void sendRequest(ClientHttpRequestInterceptor interceptor) throws Exception {
    // a real request implementation, since the HttpRequest interface has gained methods over the
    // supported spring web version range
    ClientHttpRequest request =
        new SimpleClientHttpRequestFactory()
            .createRequest(URI.create("http://localhost:8080/test"), HttpMethod.GET);
    request.getHeaders().add("X-Test-Request", "request-value");
    request.getHeaders().add("X-Secret-Token", "secret-value");

    TestClientHttpResponse response = new TestClientHttpResponse();
    response.getHeaders().add("X-Test-Response", "response-value");
    response.getHeaders().add("X-Secret-Token", "secret-value");

    interceptor.intercept(request, new byte[0], (req, body) -> response);
  }

  private static class TestClientHttpResponse implements ClientHttpResponse {

    private final HttpHeaders headers = new HttpHeaders();

    @Override
    public HttpStatus getStatusCode() {
      return HttpStatus.OK;
    }

    @Override
    public String getStatusText() {
      return "OK";
    }

    @Override
    public void close() {}

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }
  }
}
