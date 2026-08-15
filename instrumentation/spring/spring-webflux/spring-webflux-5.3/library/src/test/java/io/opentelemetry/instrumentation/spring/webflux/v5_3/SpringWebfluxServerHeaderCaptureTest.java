/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilter;

class SpringWebfluxServerHeaderCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesHeadersMatchingSelectorPatterns() {
    WebFilter filter =
        SpringWebfluxServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build()
            .createWebFilter();

    handleRequest(filter);

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
  void capturesHeadersConfiguredByName() {
    WebFilter filter =
        SpringWebfluxServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build()
            .createWebFilter();

    handleRequest(filter);

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
  void deprecatedSettersMatchHeaderNamesLiterally() {
    WebFilter filter =
        SpringWebfluxServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createWebFilter();

    handleRequest(filter);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    // "*" is a legal header name character, so it names a header rather than matching all of
    // them
    assertThat(attributes.asMap().keySet())
        .noneMatch(key -> key.getKey().startsWith("http.request.header."))
        .noneMatch(key -> key.getKey().startsWith("http.response.header."));
  }

  private static void handleRequest(WebFilter filter) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("http://localhost:8080/test")
                .header("X-Test-Request", "request-value")
                .header("X-Secret-Token", "secret-value"));

    filter
        .filter(
            exchange,
            filteredExchange -> {
              filteredExchange.getResponse().getHeaders().add("X-Test-Response", "response-value");
              filteredExchange.getResponse().getHeaders().add("X-Secret-Token", "secret-value");
              return filteredExchange.getResponse().setComplete();
            })
        .block();
  }
}
