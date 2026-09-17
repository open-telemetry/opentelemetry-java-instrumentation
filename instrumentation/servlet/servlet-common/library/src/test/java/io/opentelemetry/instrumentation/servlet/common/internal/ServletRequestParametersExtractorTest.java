/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import org.junit.jupiter.api.Test;

class ServletRequestParametersExtractorTest {

  private final Object request = new Object();

  @SuppressWarnings("unchecked")
  private final ServletAccessor<Object, Object> accessor = mock(ServletAccessor.class);

  @Test
  void capturesMatchingParameters() {
    when(accessor.getRequestParameterNames(request))
        .thenReturn(
            asList("exact", "wildcard-value", "wildcard-secret", "single-a", "single-ab", "other"));
    when(accessor.getRequestParameterValues(request, "exact")).thenReturn(singletonList("one"));
    when(accessor.getRequestParameterValues(request, "wildcard-value"))
        .thenReturn(asList("two", "three"));
    when(accessor.getRequestParameterValues(request, "single-a")).thenReturn(singletonList("four"));
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(asList("exact", "wildcard-*", "single-?"))
            .setExcluded(singletonList("*-secret"))
            .build();
    AttributesBuilder attributes = Attributes.builder();

    new ServletRequestParametersExtractor<>(accessor, selector)
        .setAttributes(request, attributes::put);

    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(stringArrayKey("servlet.request.parameter.exact"), singletonList("one")),
            entry(
                stringArrayKey("servlet.request.parameter.wildcard-value"), asList("two", "three")),
            entry(stringArrayKey("servlet.request.parameter.single-a"), singletonList("four")));
    verify(accessor, never()).getRequestParameterValues(request, "wildcard-secret");
    verify(accessor, never()).getRequestParameterValues(request, "single-ab");
    verify(accessor, never()).getRequestParameterValues(request, "other");
  }

  @Test
  void capturesAllNonExcludedParametersWithExcludeOnlySelector() {
    when(accessor.getRequestParameterNames(request)).thenReturn(asList("captured", "secret"));
    when(accessor.getRequestParameterValues(request, "captured"))
        .thenReturn(singletonList("value"));
    IncludeExclude selector = IncludeExclude.builder().setExcluded(singletonList("secret")).build();
    AttributesBuilder attributes = Attributes.builder();

    new ServletRequestParametersExtractor<>(accessor, selector)
        .setAttributes(request, attributes::put);

    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(stringArrayKey("servlet.request.parameter.captured"), singletonList("value")));
    verify(accessor, never()).getRequestParameterValues(request, "secret");
  }

  @Test
  void doesNotTouchRequestParametersForEmptySelector() {
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(emptyList()).setExcluded(emptyList()).build();

    new ServletRequestParametersExtractor<>(accessor, selector)
        .setAttributes(request, (key, value) -> {});

    verify(accessor, never()).getRequestParameterNames(request);
    verify(accessor, never()).getRequestParameterValues(request, "anything");
  }
}
