/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.enumeration;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v5_0.internal.Servlet5Accessor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ServletHeaderSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final AttributesExtractor<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      extractor =
          HttpServerAttributesExtractor.builder(
                  new ServletHttpAttributesGetter<>(Servlet5Accessor.INSTANCE))
              .setRequestHeaders(
                  IncludeExclude.builder()
                      .setIncluded(singletonList("x-*"))
                      .setExcluded(singletonList("x-secret"))
                      .build())
              .setResponseHeaders(
                  IncludeExclude.builder().setExcluded(singletonList("x-secret")).build())
              .build();

  @Test
  void capturesHeadersMatchingSelectorPatterns() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeaderNames()).thenReturn(enumeration(asList("X-Test-Request", "X-Secret")));
    when(request.getHeaders("x-test-request"))
        .thenReturn(enumeration(singletonList("request-value")));
    when(request.getHeaders("x-secret")).thenReturn(enumeration(singletonList("request-secret")));
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getHeaderNames()).thenReturn(asList("X-Test-Response", "X-Secret"));
    when(response.getHeaders("x-test-response")).thenReturn(singletonList("response-value"));
    when(response.getHeaders("x-secret")).thenReturn(singletonList("response-secret"));

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), new ServletRequestContext<>(request));
    extractor.onEnd(
        attributes,
        Context.root(),
        new ServletRequestContext<>(request),
        new ServletResponseContext<>(response),
        null);

    assertThat(attributes.build().asMap())
        .contains(
            entry(
                stringArrayKey("http.request.header.x-test-request"),
                singletonList("request-value")),
            entry(
                stringArrayKey("http.response.header.x-test-response"),
                singletonList("response-value")))
        .doesNotContainKeys(
            stringArrayKey("http.request.header.x-secret"),
            stringArrayKey("http.response.header.x-secret"));
  }

  @Test
  void requestHeaderNamesAreReiterable() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeaderNames())
        .thenAnswer(invocation -> enumeration(asList("X-Test-Request", "X-Secret")));

    Iterable<String> headerNames = Servlet5Accessor.INSTANCE.getRequestHeaderNames(request);

    // ServletRequestGetter.keys() hands this iterable to a TextMapGetter, and a propagator may read
    // it more than once
    List<String> firstPass = new ArrayList<>();
    headerNames.forEach(firstPass::add);
    List<String> secondPass = new ArrayList<>();
    headerNames.forEach(secondPass::add);

    assertThat(firstPass).containsExactly("X-Test-Request", "X-Secret");
    assertThat(secondPass).isEqualTo(firstPass);
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    Filter filter =
        ServletTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createFilter();

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeaderNames())
        .thenAnswer(invocation -> enumeration(asList("X-Test-Request", "Authorization")));
    // Authorization is present so that treating "*" as a glob would capture it
    when(request.getHeaders("x-test-request"))
        .thenAnswer(invocation -> enumeration(singletonList("request-value")));
    when(request.getHeaders("authorization"))
        .thenAnswer(invocation -> enumeration(singletonList("secret")));
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getHeaderNames()).thenAnswer(invocation -> singletonList("X-Test-Response"));
    when(response.getHeaders("x-test-response"))
        .thenAnswer(invocation -> singletonList("response-value"));

    filter.doFilter(request, response, (req, res) -> {});

    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces.get(0).get(0).getAttributes().asMap().keySet())
        .extracting(AttributeKey::getKey)
        .noneMatch(key -> key.startsWith("http.request.header."))
        .noneMatch(key -> key.startsWith("http.response.header."));
  }
}
