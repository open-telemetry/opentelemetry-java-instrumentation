/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SpringWebMvcHeaderSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesHeadersMatchingSelectorPatterns() throws Exception {
    Filter filter =
        SpringWebMvcTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build()
            .createServletFilter();

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
  void capturesHeadersConfiguredByName() throws Exception {
    Filter filter =
        SpringWebMvcTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(asList("X-Test-Request", "Authorization"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build()
            .createServletFilter();

    handleRequest(filter);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .containsExactly("request-value");
    // capturing Authorization here is what makes the assertion that it is absent in
    // deprecatedSettersMatchHeaderNamesLiterally meaningful
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization")))
        .containsExactly("secret-value");
    assertThat(attributes.get(stringArrayKey("http.request.header.x-secret-token"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .containsExactly("response-value");
    assertThat(attributes.get(stringArrayKey("http.response.header.x-secret-token"))).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    Filter filter =
        SpringWebMvcTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createServletFilter();

    handleRequest(filter);

    Attributes attributes = testing.waitForTraces(1).get(0).get(0).getAttributes();
    // "*" is matched as a literal header name, so it captures nothing because neither the request
    // nor the response contains it; Authorization ensures treating "*" as a glob would capture it
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(attributes.asMap().keySet())
        .noneMatch(key -> key.getKey().startsWith("http.request.header."))
        .noneMatch(key -> key.getKey().startsWith("http.response.header."));
  }

  private static void handleRequest(Filter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
    request.addHeader("X-Test-Request", "request-value");
    request.addHeader("X-Secret-Token", "secret-value");
    request.addHeader("Authorization", "secret-value");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, resp) -> {
          HttpServletResponse httpResponse = (HttpServletResponse) resp;
          httpResponse.addHeader("X-Test-Response", "response-value");
          httpResponse.addHeader("X-Secret-Token", "secret-value");
        });
  }
}
