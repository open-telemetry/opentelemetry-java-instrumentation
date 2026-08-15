/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.enumeration;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Servlet3Accessor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ServletHeaderCaptureTest {

  private final AttributesExtractor<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      extractor =
          HttpServerAttributesExtractor.builder(
                  new ServletHttpAttributesGetter<>(Servlet3Accessor.INSTANCE))
              .setRequestHeaders(
                  IncludeExclude.builder()
                      .setIncluded(singletonList("x-*"))
                      .setExcluded(singletonList("x-secret"))
                      .build())
              .setResponseHeaders(
                  IncludeExclude.builder().setExcluded(singletonList("x-secret")).build())
              .build();

  @Test
  void capturesHeadersMatchingSelectors() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeaderNames()).thenReturn(enumeration(asList("X-Test-Request", "X-Secret")));
    when(request.getHeaders("x-test-request"))
        .thenReturn(enumeration(singletonList("request-value")));
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getHeaderNames()).thenReturn(asList("X-Test-Response", "X-Secret"));
    when(response.getHeaders("x-test-response")).thenReturn(singletonList("response-value"));

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
}
